use anchor_lang::prelude::*;

#[error_code]
pub enum ReputationError {
    #[msg("Arithmetic overflow")]
    MathOverflow,
    #[msg("A credential requires at least one on-time repayment")]
    NotEligible,
    #[msg("The credential has already been minted")]
    AlreadyMinted,
    #[msg("Metadata URI is too long")]
    UriTooLong,
}
