use anchor_lang::prelude::*;

#[account]
#[derive(InitSpace)]
pub struct Config {
    /// Bank authority; reputation PDAs live under this config, so partners verify against a known authority.
    pub authority: Pubkey,
    pub wallets: u64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct Reputation {
    pub config: Pubkey,
    pub wallet: Pubkey,
    pub on_time_repayments: u32,
    pub late_repayments: u32,
    pub defaults: u32,
    pub total_repaid: u64,
    pub tier: u8,
    /// Soulbound credential mint; `Pubkey::default()` until the first on-time repayment.
    pub sbt_mint: Pubkey,
    pub updated_at: i64,
    pub bump: u8,
}

/// Tier 0 $200 -> Tier 1 $500 (2 on-time) -> Tier 2 $1,000 (5 on-time); each default cancels two on-time repayments.
pub fn tier_for(on_time: u32, defaults: u32) -> u8 {
    let effective = on_time.saturating_sub(defaults.saturating_mul(2));
    match effective {
        e if e >= 5 => 2,
        e if e >= 2 => 1,
        _ => 0,
    }
}
