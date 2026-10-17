use anchor_lang::prelude::*;

#[error_code]
pub enum MicroloanError {
    #[msg("Amount must be greater than zero")]
    InvalidAmount,
    #[msg("Fee must not exceed 20% of principal")]
    FeeTooHigh,
    #[msg("Months must be YYYYMM and the repayment month must be after the disbursement month")]
    InvalidMonths,
    #[msg("Loan is not in the required status for this action")]
    InvalidStatus,
    #[msg("Pool vault has insufficient funds")]
    InsufficientPoolFunds,
    #[msg("Arithmetic overflow")]
    MathOverflow,
    #[msg("Signer is not the mint authority of the pool mint")]
    NotMintAuthority,
}
