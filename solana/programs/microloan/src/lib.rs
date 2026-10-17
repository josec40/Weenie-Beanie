pub mod error;
pub mod state;

use anchor_lang::prelude::*;
use anchor_spl::token_interface::{
    approve, transfer_checked, Approve, Mint, TokenAccount, TokenInterface, TransferChecked,
};

pub use error::MicroloanError;
pub use state::*;

declare_id!("7RPbrV9Ft664iLK95bYsZbNascy9wwX57Tk6UCAM4uXk");

pub const POOL_SEED: &[u8] = b"pool";
pub const VAULT_SEED: &[u8] = b"vault";
pub const LOAN_SEED: &[u8] = b"loan";
/// Fee cap in basis points of principal.
pub const MAX_FEE_BPS: u64 = 2_000;

/// Escrowed micro-loans in a stablecoin (mock USDC) pool.
///
/// The bank authority creates a loan together with the borrower (who signs an SPL `approve` delegating
/// principal + fee to the loan PDA), disburses from the pool vault in the shortfall month, and later cranks
/// `repay`, which pulls principal + fee from the borrower via that delegation.
#[program]
pub mod microloan {
    use super::*;

    pub fn init_pool(ctx: Context<InitPool>) -> Result<()> {
        let pool = &mut ctx.accounts.pool;
        pool.authority = ctx.accounts.authority.key();
        pool.mint = ctx.accounts.mint.key();
        pool.vault = ctx.accounts.vault.key();
        pool.decimals = ctx.accounts.mint.decimals;
        pool.total_disbursed = 0;
        pool.outstanding = 0;
        pool.fees_earned = 0;
        pool.loans_created = 0;
        pool.bump = ctx.bumps.pool;
        pool.vault_bump = ctx.bumps.vault;
        Ok(())
    }

    pub fn fund_pool(ctx: Context<FundPool>, amount: u64) -> Result<()> {
        require!(amount > 0, MicroloanError::InvalidAmount);
        transfer_checked(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.authority_token.to_account_info(),
                    mint: ctx.accounts.mint.to_account_info(),
                    to: ctx.accounts.vault.to_account_info(),
                    authority: ctx.accounts.authority.to_account_info(),
                },
            ),
            amount,
            ctx.accounts.mint.decimals,
        )
    }

    pub fn create_loan(
        ctx: Context<CreateLoan>,
        loan_ref: [u8; 16],
        principal: u64,
        fee: u64,
        disburse_month: u32,
        repay_month: u32,
    ) -> Result<()> {
        require!(principal > 0, MicroloanError::InvalidAmount);
        let max_fee = principal
            .checked_mul(MAX_FEE_BPS)
            .ok_or(MicroloanError::MathOverflow)?
            / 10_000;
        require!(fee <= max_fee, MicroloanError::FeeTooHigh);
        require!(
            valid_month(disburse_month) && valid_month(repay_month) && repay_month > disburse_month,
            MicroloanError::InvalidMonths
        );
        let total_due = principal.checked_add(fee).ok_or(MicroloanError::MathOverflow)?;

        let loan = &mut ctx.accounts.loan;
        loan.pool = ctx.accounts.pool.key();
        loan.borrower = ctx.accounts.borrower.key();
        loan.borrower_token = ctx.accounts.borrower_token.key();
        loan.loan_ref = loan_ref;
        loan.principal = principal;
        loan.fee = fee;
        loan.disburse_month = disburse_month;
        loan.repay_month = repay_month;
        loan.status = LoanStatus::Created;
        loan.created_at = Clock::get()?.unix_timestamp;
        loan.disbursed_at = 0;
        loan.settled_at = 0;
        loan.bump = ctx.bumps.loan;

        // Auto-repay consent on-chain: the borrower delegates exactly principal + fee to the loan PDA.
        approve(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                Approve {
                    to: ctx.accounts.borrower_token.to_account_info(),
                    delegate: ctx.accounts.loan.to_account_info(),
                    authority: ctx.accounts.borrower.to_account_info(),
                },
            ),
            total_due,
        )?;

        let pool = &mut ctx.accounts.pool;
        pool.loans_created = pool.loans_created.checked_add(1).ok_or(MicroloanError::MathOverflow)?;

        emit!(LoanCreated {
            loan: ctx.accounts.loan.key(),
            borrower: ctx.accounts.borrower.key(),
            loan_ref,
            principal,
            fee,
            disburse_month,
            repay_month,
        });
        Ok(())
    }

    pub fn disburse(ctx: Context<Disburse>) -> Result<()> {
        require!(ctx.accounts.loan.status == LoanStatus::Created, MicroloanError::InvalidStatus);
        let principal = ctx.accounts.loan.principal;
        require!(ctx.accounts.vault.amount >= principal, MicroloanError::InsufficientPoolFunds);

        let mint_key = ctx.accounts.mint.key();
        let pool_seeds: &[&[u8]] = &[POOL_SEED, mint_key.as_ref(), &[ctx.accounts.pool.bump]];
        transfer_checked(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.vault.to_account_info(),
                    mint: ctx.accounts.mint.to_account_info(),
                    to: ctx.accounts.borrower_token.to_account_info(),
                    authority: ctx.accounts.pool.to_account_info(),
                },
                &[pool_seeds],
            ),
            principal,
            ctx.accounts.mint.decimals,
        )?;

        let loan = &mut ctx.accounts.loan;
        loan.status = LoanStatus::Disbursed;
        loan.disbursed_at = Clock::get()?.unix_timestamp;

        let pool = &mut ctx.accounts.pool;
        pool.total_disbursed = pool.total_disbursed.checked_add(principal).ok_or(MicroloanError::MathOverflow)?;
        pool.outstanding = pool.outstanding.checked_add(principal).ok_or(MicroloanError::MathOverflow)?;

        emit!(LoanDisbursed { loan: ctx.accounts.loan.key(), principal });
        Ok(())
    }

    pub fn repay(ctx: Context<Repay>) -> Result<()> {
        require!(ctx.accounts.loan.status == LoanStatus::Disbursed, MicroloanError::InvalidStatus);
        let principal = ctx.accounts.loan.principal;
        let fee = ctx.accounts.loan.fee;
        let total_due = ctx.accounts.loan.total_due().ok_or(MicroloanError::MathOverflow)?;

        let pool_key = ctx.accounts.pool.key();
        let loan_ref = ctx.accounts.loan.loan_ref;
        let loan_seeds: &[&[u8]] = &[LOAN_SEED, pool_key.as_ref(), loan_ref.as_ref(), &[ctx.accounts.loan.bump]];
        transfer_checked(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.borrower_token.to_account_info(),
                    mint: ctx.accounts.mint.to_account_info(),
                    to: ctx.accounts.vault.to_account_info(),
                    authority: ctx.accounts.loan.to_account_info(),
                },
                &[loan_seeds],
            ),
            total_due,
            ctx.accounts.mint.decimals,
        )?;

        let loan = &mut ctx.accounts.loan;
        loan.status = LoanStatus::Repaid;
        loan.settled_at = Clock::get()?.unix_timestamp;

        let pool = &mut ctx.accounts.pool;
        pool.outstanding = pool.outstanding.checked_sub(principal).ok_or(MicroloanError::MathOverflow)?;
        pool.fees_earned = pool.fees_earned.checked_add(fee).ok_or(MicroloanError::MathOverflow)?;

        emit!(LoanRepaid { loan: ctx.accounts.loan.key(), amount: total_due });
        Ok(())
    }

    pub fn mark_default(ctx: Context<MarkDefault>) -> Result<()> {
        require!(ctx.accounts.loan.status == LoanStatus::Disbursed, MicroloanError::InvalidStatus);
        let principal = ctx.accounts.loan.principal;

        let loan = &mut ctx.accounts.loan;
        loan.status = LoanStatus::Defaulted;
        loan.settled_at = Clock::get()?.unix_timestamp;

        let pool = &mut ctx.accounts.pool;
        pool.outstanding = pool.outstanding.checked_sub(principal).ok_or(MicroloanError::MathOverflow)?;

        emit!(LoanDefaulted { loan: ctx.accounts.loan.key(), principal });
        Ok(())
    }
}

fn valid_month(yyyymm: u32) -> bool {
    let month = yyyymm % 100;
    (200_001..=299_912).contains(&yyyymm) && (1..=12).contains(&month)
}

#[derive(Accounts)]
pub struct InitPool<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    // Only the stablecoin's mint authority (the bank) can open the pool, so the PDA cannot be squatted.
    #[account(
        mint::token_program = token_program,
        constraint = Option::<Pubkey>::from(mint.mint_authority) == Some(authority.key()) @ MicroloanError::NotMintAuthority,
    )]
    pub mint: InterfaceAccount<'info, Mint>,
    #[account(
        init,
        payer = authority,
        space = 8 + Pool::INIT_SPACE,
        seeds = [POOL_SEED, mint.key().as_ref()],
        bump,
    )]
    pub pool: Account<'info, Pool>,
    #[account(
        init,
        payer = authority,
        seeds = [VAULT_SEED, pool.key().as_ref()],
        bump,
        token::mint = mint,
        token::authority = pool,
        token::token_program = token_program,
    )]
    pub vault: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct FundPool<'info> {
    pub authority: Signer<'info>,
    #[account(
        seeds = [POOL_SEED, mint.key().as_ref()],
        bump = pool.bump,
        has_one = authority,
        has_one = mint,
        has_one = vault,
    )]
    pub pool: Account<'info, Pool>,
    #[account(mint::token_program = token_program)]
    pub mint: InterfaceAccount<'info, Mint>,
    #[account(mut)]
    pub vault: InterfaceAccount<'info, TokenAccount>,
    #[account(
        mut,
        token::mint = mint,
        token::authority = authority,
        token::token_program = token_program,
    )]
    pub authority_token: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
}

#[derive(Accounts)]
#[instruction(loan_ref: [u8; 16])]
pub struct CreateLoan<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    /// Custodial borrower wallet; signs the SPL approve that authorizes auto-repayment.
    pub borrower: Signer<'info>,
    #[account(
        mut,
        seeds = [POOL_SEED, mint.key().as_ref()],
        bump = pool.bump,
        has_one = authority,
        has_one = mint,
    )]
    pub pool: Account<'info, Pool>,
    #[account(mint::token_program = token_program)]
    pub mint: InterfaceAccount<'info, Mint>,
    #[account(
        mut,
        token::mint = mint,
        token::authority = borrower,
        token::token_program = token_program,
    )]
    pub borrower_token: InterfaceAccount<'info, TokenAccount>,
    #[account(
        init,
        payer = authority,
        space = 8 + Loan::INIT_SPACE,
        seeds = [LOAN_SEED, pool.key().as_ref(), loan_ref.as_ref()],
        bump,
    )]
    pub loan: Account<'info, Loan>,
    pub token_program: Interface<'info, TokenInterface>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct Disburse<'info> {
    pub authority: Signer<'info>,
    #[account(
        mut,
        seeds = [POOL_SEED, mint.key().as_ref()],
        bump = pool.bump,
        has_one = authority,
        has_one = mint,
        has_one = vault,
    )]
    pub pool: Account<'info, Pool>,
    #[account(
        mut,
        seeds = [LOAN_SEED, pool.key().as_ref(), loan.loan_ref.as_ref()],
        bump = loan.bump,
        has_one = pool,
        has_one = borrower_token,
    )]
    pub loan: Account<'info, Loan>,
    #[account(mint::token_program = token_program)]
    pub mint: InterfaceAccount<'info, Mint>,
    #[account(mut)]
    pub vault: InterfaceAccount<'info, TokenAccount>,
    #[account(mut, token::mint = mint, token::token_program = token_program)]
    pub borrower_token: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
}

#[derive(Accounts)]
pub struct Repay<'info> {
    pub authority: Signer<'info>,
    #[account(
        mut,
        seeds = [POOL_SEED, mint.key().as_ref()],
        bump = pool.bump,
        has_one = authority,
        has_one = mint,
        has_one = vault,
    )]
    pub pool: Account<'info, Pool>,
    #[account(
        mut,
        seeds = [LOAN_SEED, pool.key().as_ref(), loan.loan_ref.as_ref()],
        bump = loan.bump,
        has_one = pool,
        has_one = borrower_token,
    )]
    pub loan: Account<'info, Loan>,
    #[account(mint::token_program = token_program)]
    pub mint: InterfaceAccount<'info, Mint>,
    #[account(mut)]
    pub vault: InterfaceAccount<'info, TokenAccount>,
    #[account(mut, token::mint = mint, token::token_program = token_program)]
    pub borrower_token: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
}

#[derive(Accounts)]
pub struct MarkDefault<'info> {
    pub authority: Signer<'info>,
    #[account(
        mut,
        seeds = [POOL_SEED, pool.mint.as_ref()],
        bump = pool.bump,
        has_one = authority,
    )]
    pub pool: Account<'info, Pool>,
    #[account(
        mut,
        seeds = [LOAN_SEED, pool.key().as_ref(), loan.loan_ref.as_ref()],
        bump = loan.bump,
        has_one = pool,
    )]
    pub loan: Account<'info, Loan>,
}

#[event]
pub struct LoanCreated {
    pub loan: Pubkey,
    pub borrower: Pubkey,
    pub loan_ref: [u8; 16],
    pub principal: u64,
    pub fee: u64,
    pub disburse_month: u32,
    pub repay_month: u32,
}

#[event]
pub struct LoanDisbursed {
    pub loan: Pubkey,
    pub principal: u64,
}

#[event]
pub struct LoanRepaid {
    pub loan: Pubkey,
    pub amount: u64,
}

#[event]
pub struct LoanDefaulted {
    pub loan: Pubkey,
    pub principal: u64,
}
