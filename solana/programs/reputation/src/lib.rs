pub mod error;
pub mod state;

use anchor_lang::prelude::*;
use anchor_lang::system_program::{create_account, CreateAccount};
use anchor_spl::associated_token::{self, get_associated_token_address_with_program_id, AssociatedToken};
use anchor_spl::token_2022::spl_token_2022::{
    extension::ExtensionType, instruction::AuthorityType, state::Mint as Token2022Mint,
};
use anchor_spl::token_interface::{
    initialize_mint2, metadata_pointer_initialize, mint_to, non_transferable_mint_initialize, set_authority,
    spl_token_metadata_interface::state::TokenMetadata, token_metadata_initialize, InitializeMint2,
    MetadataPointerInitialize, MintTo, NonTransferableMintInitialize, SetAuthority, Token2022,
    TokenMetadataInitialize,
};

pub use error::ReputationError;
pub use state::*;

declare_id!("HicA6FJxaS4DTqZL5reqxHmYcXyEKBsPkJnguMUPVnsh");

pub const CONFIG_SEED: &[u8] = b"config";
pub const REPUTATION_SEED: &[u8] = b"reputation";
pub const SBT_SEED: &[u8] = b"sbt";
pub const SBT_NAME: &str = "ShBean Repayment Credential";
pub const SBT_SYMBOL: &str = "SHBREP";
pub const MAX_URI_LEN: usize = 200;

/// Non-transferable repayment reputation. Only the bank authority that owns a config can write to the
/// reputation PDAs under it; anyone can read them (public verification, no PII on-chain).
#[program]
pub mod reputation {
    use super::*;

    pub fn init_config(ctx: Context<InitConfig>) -> Result<()> {
        let config = &mut ctx.accounts.config;
        config.authority = ctx.accounts.authority.key();
        config.wallets = 0;
        config.bump = ctx.bumps.config;
        Ok(())
    }

    pub fn init_reputation(ctx: Context<InitReputation>) -> Result<()> {
        let rep = &mut ctx.accounts.reputation;
        rep.config = ctx.accounts.config.key();
        rep.wallet = ctx.accounts.wallet.key();
        rep.on_time_repayments = 0;
        rep.late_repayments = 0;
        rep.defaults = 0;
        rep.total_repaid = 0;
        rep.tier = 0;
        rep.sbt_mint = Pubkey::default();
        rep.updated_at = Clock::get()?.unix_timestamp;
        rep.bump = ctx.bumps.reputation;

        let config = &mut ctx.accounts.config;
        config.wallets = config.wallets.checked_add(1).ok_or(ReputationError::MathOverflow)?;
        Ok(())
    }

    pub fn record_repayment(ctx: Context<UpdateReputation>, on_time: bool, amount: u64) -> Result<()> {
        let rep = &mut ctx.accounts.reputation;
        if on_time {
            rep.on_time_repayments = rep.on_time_repayments.checked_add(1).ok_or(ReputationError::MathOverflow)?;
        } else {
            rep.late_repayments = rep.late_repayments.checked_add(1).ok_or(ReputationError::MathOverflow)?;
        }
        rep.total_repaid = rep.total_repaid.checked_add(amount).ok_or(ReputationError::MathOverflow)?;
        rep.tier = tier_for(rep.on_time_repayments, rep.defaults);
        rep.updated_at = Clock::get()?.unix_timestamp;
        emit!(ReputationUpdated { wallet: rep.wallet, tier: rep.tier, on_time: rep.on_time_repayments, defaults: rep.defaults });
        Ok(())
    }

    pub fn record_default(ctx: Context<UpdateReputation>) -> Result<()> {
        let rep = &mut ctx.accounts.reputation;
        rep.defaults = rep.defaults.checked_add(1).ok_or(ReputationError::MathOverflow)?;
        rep.tier = tier_for(rep.on_time_repayments, rep.defaults);
        rep.updated_at = Clock::get()?.unix_timestamp;
        emit!(ReputationUpdated { wallet: rep.wallet, tier: rep.tier, on_time: rep.on_time_repayments, defaults: rep.defaults });
        Ok(())
    }

    /// Mints the Token-2022 soulbound credential (NonTransferable + on-mint metadata), supply fixed at 1.
    pub fn mint_credential(ctx: Context<MintCredential>, uri: String) -> Result<()> {
        require!(uri.len() <= MAX_URI_LEN, ReputationError::UriTooLong);
        require!(ctx.accounts.reputation.on_time_repayments >= 1, ReputationError::NotEligible);
        require!(ctx.accounts.reputation.sbt_mint == Pubkey::default(), ReputationError::AlreadyMinted);

        let token_program_id = ctx.accounts.token_program.key();
        let config_key = ctx.accounts.config.key();
        let mint_key = ctx.accounts.sbt_mint.key();
        let reputation_key = ctx.accounts.reputation.key();
        let authority_key = ctx.accounts.authority.key();
        let mint_bump = [ctx.bumps.sbt_mint];
        let mint_seeds: &[&[u8]] = &[SBT_SEED, reputation_key.as_ref(), &mint_bump];
        let config_bump = [ctx.accounts.config.bump];
        let config_seeds: &[&[u8]] = &[CONFIG_SEED, authority_key.as_ref(), &config_bump];

        // Allocate exactly the fixed extensions; fund the variable-length metadata the token program reallocs into.
        let space = ExtensionType::try_calculate_account_len::<Token2022Mint>(&[
            ExtensionType::NonTransferable,
            ExtensionType::MetadataPointer,
        ])?;
        let metadata = TokenMetadata {
            name: SBT_NAME.to_string(),
            symbol: SBT_SYMBOL.to_string(),
            uri: uri.clone(),
            ..Default::default()
        };
        let lamports = Rent::get()?.minimum_balance(space + metadata.tlv_size_of()?);
        create_account(
            CpiContext::new_with_signer(
                ctx.accounts.system_program.key(),
                CreateAccount {
                    from: ctx.accounts.authority.to_account_info(),
                    to: ctx.accounts.sbt_mint.to_account_info(),
                },
                &[mint_seeds],
            ),
            lamports,
            space as u64,
            &token_program_id,
        )?;

        non_transferable_mint_initialize(CpiContext::new(
            token_program_id,
            NonTransferableMintInitialize {
                token_program_id: ctx.accounts.token_program.to_account_info(),
                mint: ctx.accounts.sbt_mint.to_account_info(),
            },
        ))?;
        metadata_pointer_initialize(
            CpiContext::new(
                token_program_id,
                MetadataPointerInitialize {
                    token_program_id: ctx.accounts.token_program.to_account_info(),
                    mint: ctx.accounts.sbt_mint.to_account_info(),
                },
            ),
            Some(config_key),
            Some(mint_key),
        )?;
        initialize_mint2(
            CpiContext::new(token_program_id, InitializeMint2 { mint: ctx.accounts.sbt_mint.to_account_info() }),
            0,
            &config_key,
            None,
        )?;
        token_metadata_initialize(
            CpiContext::new_with_signer(
                token_program_id,
                TokenMetadataInitialize {
                    program_id: ctx.accounts.token_program.to_account_info(),
                    metadata: ctx.accounts.sbt_mint.to_account_info(),
                    update_authority: ctx.accounts.config.to_account_info(),
                    mint_authority: ctx.accounts.config.to_account_info(),
                    mint: ctx.accounts.sbt_mint.to_account_info(),
                },
                &[config_seeds],
            ),
            metadata.name,
            metadata.symbol,
            uri,
        )?;

        associated_token::create(CpiContext::new(
            ctx.accounts.associated_token_program.key(),
            associated_token::Create {
                payer: ctx.accounts.authority.to_account_info(),
                associated_token: ctx.accounts.wallet_sbt_account.to_account_info(),
                authority: ctx.accounts.wallet.to_account_info(),
                mint: ctx.accounts.sbt_mint.to_account_info(),
                system_program: ctx.accounts.system_program.to_account_info(),
                token_program: ctx.accounts.token_program.to_account_info(),
            },
        ))?;
        mint_to(
            CpiContext::new_with_signer(
                token_program_id,
                MintTo {
                    mint: ctx.accounts.sbt_mint.to_account_info(),
                    to: ctx.accounts.wallet_sbt_account.to_account_info(),
                    authority: ctx.accounts.config.to_account_info(),
                },
                &[config_seeds],
            ),
            1,
        )?;
        // Supply is fixed at exactly one credential per wallet.
        set_authority(
            CpiContext::new_with_signer(
                token_program_id,
                SetAuthority {
                    current_authority: ctx.accounts.config.to_account_info(),
                    account_or_mint: ctx.accounts.sbt_mint.to_account_info(),
                },
                &[config_seeds],
            ),
            AuthorityType::MintTokens,
            None,
        )?;

        let rep = &mut ctx.accounts.reputation;
        rep.sbt_mint = mint_key;
        rep.updated_at = Clock::get()?.unix_timestamp;
        emit!(CredentialMinted { wallet: rep.wallet, mint: mint_key });
        Ok(())
    }
}

#[derive(Accounts)]
pub struct InitConfig<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(
        init,
        payer = authority,
        space = 8 + Config::INIT_SPACE,
        seeds = [CONFIG_SEED, authority.key().as_ref()],
        bump,
    )]
    pub config: Account<'info, Config>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct InitReputation<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(
        mut,
        seeds = [CONFIG_SEED, authority.key().as_ref()],
        bump = config.bump,
        has_one = authority,
    )]
    pub config: Account<'info, Config>,
    /// CHECK: any wallet address; only its key is stored and used as a PDA seed.
    pub wallet: UncheckedAccount<'info>,
    #[account(
        init,
        payer = authority,
        space = 8 + Reputation::INIT_SPACE,
        seeds = [REPUTATION_SEED, config.key().as_ref(), wallet.key().as_ref()],
        bump,
    )]
    pub reputation: Account<'info, Reputation>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct UpdateReputation<'info> {
    pub authority: Signer<'info>,
    #[account(
        seeds = [CONFIG_SEED, authority.key().as_ref()],
        bump = config.bump,
        has_one = authority,
    )]
    pub config: Account<'info, Config>,
    #[account(
        mut,
        seeds = [REPUTATION_SEED, config.key().as_ref(), reputation.wallet.as_ref()],
        bump = reputation.bump,
        has_one = config,
    )]
    pub reputation: Account<'info, Reputation>,
}

#[derive(Accounts)]
pub struct MintCredential<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(
        seeds = [CONFIG_SEED, authority.key().as_ref()],
        bump = config.bump,
        has_one = authority,
    )]
    pub config: Account<'info, Config>,
    #[account(
        mut,
        seeds = [REPUTATION_SEED, config.key().as_ref(), wallet.key().as_ref()],
        bump = reputation.bump,
        has_one = config,
        has_one = wallet,
    )]
    pub reputation: Account<'info, Reputation>,
    /// CHECK: the credential holder; bound to the reputation PDA via `has_one = wallet`.
    pub wallet: UncheckedAccount<'info>,
    /// CHECK: created and initialized in the handler as a Token-2022 mint at this PDA.
    #[account(mut, seeds = [SBT_SEED, reputation.key().as_ref()], bump)]
    pub sbt_mint: UncheckedAccount<'info>,
    /// CHECK: created in the handler via the associated token program; address is pinned here.
    #[account(
        mut,
        address = get_associated_token_address_with_program_id(&wallet.key(), &sbt_mint.key(), &token_program.key()),
    )]
    pub wallet_sbt_account: UncheckedAccount<'info>,
    pub token_program: Program<'info, Token2022>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[event]
pub struct ReputationUpdated {
    pub wallet: Pubkey,
    pub tier: u8,
    pub on_time: u32,
    pub defaults: u32,
}

#[event]
pub struct CredentialMinted {
    pub wallet: Pubkey,
    pub mint: Pubkey,
}
