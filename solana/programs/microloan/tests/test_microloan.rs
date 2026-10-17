use {
    anchor_lang::{
        prelude::Pubkey,
        solana_program::{
            instruction::{AccountMeta, Instruction},
            program_pack::Pack,
            system_instruction, system_program,
        },
        AccountDeserialize, InstructionData, ToAccountMetas,
    },
    anchor_spl::{
        associated_token::{get_associated_token_address, spl_associated_token_account},
        token::spl_token,
    },
    litesvm::LiteSVM,
    microloan::{LoanStatus, LOAN_SEED, POOL_SEED, VAULT_SEED},
    solana_keypair::Keypair,
    solana_message::{Message, VersionedMessage},
    solana_signer::Signer,
    solana_transaction::versioned::VersionedTransaction,
};

const USDC: u64 = 1_000_000;
const DECIMALS: u8 = 6;

struct Keys {
    bank: Keypair,
    borrower: Keypair,
    mint: Pubkey,
    bank_ata: Pubkey,
    borrower_ata: Pubkey,
    pool: Pubkey,
    vault: Pubkey,
}

struct Env {
    svm: LiteSVM,
    k: Keys,
}

fn send(svm: &mut LiteSVM, ixs: &[Instruction], signers: &[&Keypair]) -> Result<(), String> {
    let msg = Message::new_with_blockhash(ixs, Some(&signers[0].pubkey()), &svm.latest_blockhash());
    let tx = VersionedTransaction::try_new(VersionedMessage::Legacy(msg), signers).unwrap();
    svm.send_transaction(tx).map(|_| ()).map_err(|e| e.meta.logs.join("\n"))
}

fn setup() -> Env {
    let mut svm = LiteSVM::new();
    let bytes = include_bytes!(concat!(env!("CARGO_TARGET_TMPDIR"), "/../deploy/microloan.so"));
    svm.add_program(microloan::id(), bytes).unwrap();

    let bank = Keypair::new();
    let borrower = Keypair::new();
    svm.airdrop(&bank.pubkey(), 10_000_000_000).unwrap();
    svm.airdrop(&borrower.pubkey(), 1_000_000_000).unwrap();

    // Mock USDC: classic SPL token, 6 decimals, mint authority = bank (decision 5).
    let mint = Keypair::new();
    let rent = svm.minimum_balance_for_rent_exemption(spl_token::state::Mint::LEN);
    send(
        &mut svm,
        &[
            system_instruction::create_account(&bank.pubkey(), &mint.pubkey(), rent, spl_token::state::Mint::LEN as u64, &spl_token::ID),
            spl_token::instruction::initialize_mint2(&spl_token::ID, &mint.pubkey(), &bank.pubkey(), None, DECIMALS).unwrap(),
        ],
        &[&bank, &mint],
    )
    .unwrap();

    let bank_ata = get_associated_token_address(&bank.pubkey(), &mint.pubkey());
    let borrower_ata = get_associated_token_address(&borrower.pubkey(), &mint.pubkey());
    send(
        &mut svm,
        &[
            spl_associated_token_account::instruction::create_associated_token_account(&bank.pubkey(), &bank.pubkey(), &mint.pubkey(), &spl_token::ID),
            spl_associated_token_account::instruction::create_associated_token_account(&bank.pubkey(), &borrower.pubkey(), &mint.pubkey(), &spl_token::ID),
            spl_token::instruction::mint_to(&spl_token::ID, &mint.pubkey(), &bank_ata, &bank.pubkey(), &[], 10_000 * USDC).unwrap(),
        ],
        &[&bank],
    )
    .unwrap();

    let pool = Pubkey::find_program_address(&[POOL_SEED, mint.pubkey().as_ref()], &microloan::id()).0;
    let vault = Pubkey::find_program_address(&[VAULT_SEED, pool.as_ref()], &microloan::id()).0;
    Env { svm, k: Keys { bank, borrower, mint: mint.pubkey(), bank_ata, borrower_ata, pool, vault } }
}

fn ix(data: impl InstructionData, accounts: impl ToAccountMetas) -> Instruction {
    Instruction::new_with_bytes(microloan::id(), &data.data(), accounts.to_account_metas(None))
}

fn init_pool_ix(env: &Keys, authority: &Pubkey) -> Instruction {
    ix(
        microloan::instruction::InitPool {},
        microloan::accounts::InitPool {
            authority: *authority,
            mint: env.mint,
            pool: env.pool,
            vault: env.vault,
            token_program: spl_token::ID,
            system_program: system_program::ID,
        },
    )
}

fn loan_pda(env: &Keys, loan_ref: [u8; 16]) -> Pubkey {
    Pubkey::find_program_address(&[LOAN_SEED, env.pool.as_ref(), loan_ref.as_ref()], &microloan::id()).0
}

fn create_loan_ix(env: &Keys, loan_ref: [u8; 16], principal: u64, fee: u64, months: (u32, u32)) -> Instruction {
    ix(
        microloan::instruction::CreateLoan { loan_ref, principal, fee, disburse_month: months.0, repay_month: months.1 },
        microloan::accounts::CreateLoan {
            authority: env.bank.pubkey(),
            borrower: env.borrower.pubkey(),
            pool: env.pool,
            mint: env.mint,
            borrower_token: env.borrower_ata,
            loan: loan_pda(env, loan_ref),
            token_program: spl_token::ID,
            system_program: system_program::ID,
        },
    )
}

fn settle_accounts(env: &Keys, authority: Pubkey, loan: Pubkey) -> microloan::accounts::Disburse {
    microloan::accounts::Disburse {
        authority,
        pool: env.pool,
        loan,
        mint: env.mint,
        vault: env.vault,
        borrower_token: env.borrower_ata,
        token_program: spl_token::ID,
    }
}

fn repay_ix(env: &Keys, loan: Pubkey) -> Instruction {
    ix(
        microloan::instruction::Repay {},
        microloan::accounts::Repay {
            authority: env.bank.pubkey(),
            pool: env.pool,
            loan,
            mint: env.mint,
            vault: env.vault,
            borrower_token: env.borrower_ata,
            token_program: spl_token::ID,
        },
    )
}

fn token_account(svm: &LiteSVM, address: &Pubkey) -> spl_token::state::Account {
    spl_token::state::Account::unpack(&svm.get_account(address).unwrap().data).unwrap()
}

fn read<T: AccountDeserialize>(svm: &LiteSVM, address: &Pubkey) -> T {
    T::try_deserialize(&mut svm.get_account(address).unwrap().data.as_slice()).unwrap()
}

fn funded(mut env: Env) -> Env {
    let bank = env.k.bank.insecure_clone();
    send(&mut env.svm, &[init_pool_ix(&env.k, &bank.pubkey())], &[&bank]).unwrap();
    let fund = ix(
        microloan::instruction::FundPool { amount: 1_000 * USDC },
        microloan::accounts::FundPool {
            authority: bank.pubkey(),
            pool: env.k.pool,
            mint: env.k.mint,
            vault: env.k.vault,
            authority_token: env.k.bank_ata,
            token_program: spl_token::ID,
        },
    );
    send(&mut env.svm, &[fund], &[&bank]).unwrap();
    env
}

#[test]
fn full_lifecycle_disburse_and_auto_repay_via_delegation() {
    let mut env = funded(setup());
    let bank = env.k.bank.insecure_clone();
    let borrower = env.k.borrower.insecure_clone();
    let loan_ref = [7u8; 16];
    let loan = loan_pda(&env.k, loan_ref);
    let (principal, fee) = (342 * USDC, 17_100_000);

    send(&mut env.svm, &[create_loan_ix(&env.k, loan_ref, principal, fee, (202702, 202704))], &[&bank, &borrower]).unwrap();
    let ata = token_account(&env.svm, &env.k.borrower_ata);
    assert_eq!(Option::<Pubkey>::from(ata.delegate), Some(loan));
    assert_eq!(ata.delegated_amount, principal + fee);
    assert_eq!(read::<microloan::Loan>(&env.svm, &loan).status, LoanStatus::Created);

    send(&mut env.svm, &[ix(microloan::instruction::Disburse {}, settle_accounts(&env.k, bank.pubkey(), loan))], &[&bank]).unwrap();
    assert_eq!(token_account(&env.svm, &env.k.borrower_ata).amount, principal);
    assert_eq!(token_account(&env.svm, &env.k.vault).amount, 1_000 * USDC - principal);
    let pool: microloan::Pool = read(&env.svm, &env.k.pool);
    assert_eq!((pool.outstanding, pool.total_disbursed, pool.loans_created), (principal, principal, 1));

    // Surplus month: income arrives, then the bank cranks repay; the borrower does not sign.
    send(
        &mut env.svm,
        &[spl_token::instruction::mint_to(&spl_token::ID, &env.k.mint, &env.k.borrower_ata, &bank.pubkey(), &[], 100 * USDC).unwrap()],
        &[&bank],
    )
    .unwrap();
    send(&mut env.svm, &[repay_ix(&env.k, loan)], &[&bank]).unwrap();

    assert_eq!(token_account(&env.svm, &env.k.borrower_ata).amount, 100 * USDC - fee);
    assert_eq!(token_account(&env.svm, &env.k.vault).amount, 1_000 * USDC + fee);
    let pool: microloan::Pool = read(&env.svm, &env.k.pool);
    assert_eq!((pool.outstanding, pool.fees_earned), (0, fee));
    let stored: microloan::Loan = read(&env.svm, &loan);
    assert_eq!(stored.status, LoanStatus::Repaid);
    assert!(stored.settled_at >= stored.disbursed_at);
}

#[test]
fn only_the_stablecoin_mint_authority_can_open_the_pool() {
    let mut env = setup();
    let attacker = Keypair::new();
    env.svm.airdrop(&attacker.pubkey(), 1_000_000_000).unwrap();
    let err = send(&mut env.svm, &[init_pool_ix(&env.k, &attacker.pubkey())], &[&attacker]).unwrap_err();
    assert!(err.contains("NotMintAuthority"), "{err}");
}

#[test]
fn rejects_invalid_terms_wrong_signers_and_out_of_order_steps() {
    let mut env = funded(setup());
    let bank = env.k.bank.insecure_clone();
    let borrower = env.k.borrower.insecure_clone();
    let attacker = Keypair::new();
    env.svm.airdrop(&attacker.pubkey(), 1_000_000_000).unwrap();

    let fee_too_high = create_loan_ix(&env.k, [1u8; 16], 100 * USDC, 21 * USDC, (202702, 202704));
    assert!(send(&mut env.svm, &[fee_too_high], &[&bank, &borrower]).unwrap_err().contains("FeeTooHigh"));
    let bad_months = create_loan_ix(&env.k, [1u8; 16], 100 * USDC, USDC, (202704, 202702));
    assert!(send(&mut env.svm, &[bad_months], &[&bank, &borrower]).unwrap_err().contains("InvalidMonths"));

    // The borrower's signature (the on-chain consent to auto-repay) is mandatory.
    let mut unsigned = create_loan_ix(&env.k, [2u8; 16], 100 * USDC, USDC, (202702, 202704));
    unsigned.accounts.iter_mut().filter(|m| m.pubkey == borrower.pubkey()).for_each(|m| *m = AccountMeta::new_readonly(m.pubkey, false));
    assert!(send(&mut env.svm, &[unsigned], &[&bank]).is_err());

    let loan_ref = [3u8; 16];
    let loan = loan_pda(&env.k, loan_ref);
    send(&mut env.svm, &[create_loan_ix(&env.k, loan_ref, 100 * USDC, 5 * USDC, (202702, 202704))], &[&bank, &borrower]).unwrap();

    assert!(send(&mut env.svm, &[repay_ix(&env.k, loan)], &[&bank]).unwrap_err().contains("InvalidStatus"));
    let by_attacker = ix(microloan::instruction::Disburse {}, settle_accounts(&env.k, attacker.pubkey(), loan));
    assert!(send(&mut env.svm, &[by_attacker], &[&attacker]).is_err());

    send(&mut env.svm, &[ix(microloan::instruction::Disburse {}, settle_accounts(&env.k, bank.pubkey(), loan))], &[&bank]).unwrap();
    let again = ix(microloan::instruction::Disburse {}, settle_accounts(&env.k, bank.pubkey(), loan));
    env.svm.expire_blockhash();
    assert!(send(&mut env.svm, &[again], &[&bank]).unwrap_err().contains("InvalidStatus"));

    let default_ix = ix(
        microloan::instruction::MarkDefault {},
        microloan::accounts::MarkDefault { authority: bank.pubkey(), pool: env.k.pool, loan },
    );
    send(&mut env.svm, &[default_ix], &[&bank]).unwrap();
    assert_eq!(read::<microloan::Loan>(&env.svm, &loan).status, LoanStatus::Defaulted);
    assert_eq!(read::<microloan::Pool>(&env.svm, &env.k.pool).outstanding, 0);
    env.svm.expire_blockhash();
    assert!(send(&mut env.svm, &[repay_ix(&env.k, loan)], &[&bank]).unwrap_err().contains("InvalidStatus"));
}

/// Golden vector shared with the Java client (backend `AnchorCodecTest`): Anchor discriminator + Borsh args.
#[test]
fn create_loan_instruction_data_matches_java_golden_vector() {
    let mut loan_ref = [0u8; 16];
    loan_ref.iter_mut().enumerate().for_each(|(i, b)| *b = i as u8);
    let data = microloan::instruction::CreateLoan {
        loan_ref,
        principal: 342_000_000,
        fee: 17_100_000,
        disburse_month: 202702,
        repay_month: 202704,
    }
    .data();
    let hex: String = data.iter().map(|b| format!("{b:02x}")).collect();
    assert_eq!(
        hex,
        "a68376db8adace8c000102030405060708090a0b0c0d0e0f8081621400000000e0ec040100000000ce170300d0170300"
    );
}

/// PDA golden vectors shared with the Java client (backend `SolanajClientTest`), for the live devnet mint.
#[test]
fn pda_derivation_golden_vectors() {
    use std::str::FromStr;
    let mint = Pubkey::from_str("Cmg8i52JnPxSXQ67a8pYaWBgJtUoMek5oz6bgb5DuNM3").unwrap();
    let (pool, pool_bump) = Pubkey::find_program_address(&[POOL_SEED, mint.as_ref()], &microloan::id());
    let (vault, vault_bump) = Pubkey::find_program_address(&[VAULT_SEED, pool.as_ref()], &microloan::id());
    let loan_ref: Vec<u8> = (0u8..16).collect();
    let (loan, loan_bump) = Pubkey::find_program_address(&[LOAN_SEED, pool.as_ref(), &loan_ref], &microloan::id());
    assert_eq!((pool.to_string().as_str(), pool_bump), ("E4Vw2myrgtZBeUx4QVEBuoYj9kmVDr4jaEJLpHKvcmem", 255));
    assert_eq!((vault.to_string().as_str(), vault_bump), ("13UD7PwE7B1vZSgPnw5AYpoydgfPWSsNUam6ozgBV4qJ", 255));
    assert_eq!((loan.to_string().as_str(), loan_bump), ("EDNJQFxcV3jLEJ6t4SRa4jsDvXt3uxAunP67f8rZqkxC", 255));
}
