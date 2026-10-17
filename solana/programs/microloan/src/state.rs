use anchor_lang::prelude::*;

#[account]
#[derive(InitSpace)]
pub struct Pool {
    /// Bank authority: the only signer allowed to create, disburse, repay or default loans.
    pub authority: Pubkey,
    pub mint: Pubkey,
    pub vault: Pubkey,
    pub decimals: u8,
    pub total_disbursed: u64,
    pub outstanding: u64,
    pub fees_earned: u64,
    pub loans_created: u64,
    pub bump: u8,
    pub vault_bump: u8,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq, InitSpace, Debug)]
pub enum LoanStatus {
    Created,
    Disbursed,
    Repaid,
    Defaulted,
}

#[account]
#[derive(InitSpace)]
pub struct Loan {
    pub pool: Pubkey,
    pub borrower: Pubkey,
    pub borrower_token: Pubkey,
    /// Backend loan UUID (16 bytes); also a PDA seed, so one on-chain loan per backend loan.
    pub loan_ref: [u8; 16],
    pub principal: u64,
    pub fee: u64,
    /// YYYYMM business months set by the bank; timing is enforced off-chain by the authority (decision 7).
    pub disburse_month: u32,
    pub repay_month: u32,
    pub status: LoanStatus,
    pub created_at: i64,
    pub disbursed_at: i64,
    pub settled_at: i64,
    pub bump: u8,
}

impl Loan {
    pub fn total_due(&self) -> Option<u64> {
        self.principal.checked_add(self.fee)
    }
}
