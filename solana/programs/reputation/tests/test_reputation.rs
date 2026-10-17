use {
    anchor_lang::{
        prelude::Pubkey,
        solana_program::{instruction::Instruction, system_program},
        AccountDeserialize, InstructionData, ToAccountMetas,
    },
    anchor_spl::{
        associated_token::{get_associated_token_address_with_program_id, spl_associated_token_account},
        token_2022::spl_token_2022::{
            self,
            extension::{
                metadata_pointer::MetadataPointer, non_transferable::NonTransferable, BaseStateWithExtensions,
                StateWithExtensions,
            },
            state::{Account as TokenAccount2022, Mint as Mint2022},
        },
        token_interface::spl_token_metadata_interface::state::TokenMetadata,
    },
    litesvm::LiteSVM,
    reputation::{Reputation, CONFIG_SEED, REPUTATION_SEED, SBT_NAME, SBT_SEED, SBT_SYMBOL},
    solana_keypair::Keypair,
    solana_message::{Message, VersionedMessage},
    solana_signer::Signer,
    solana_transaction::versioned::VersionedTransaction,
};

const URI: &str = "https://shbean.example/api/public/credentials/";

struct Keys {
    bank: Keypair,
    wallet: Keypair,
    config: Pubkey,
    reputation: Pubkey,
    sbt_mint: Pubkey,
    wallet_sbt: Pubkey,
}

struct Env {
    svm: LiteSVM,
    k: Keys,
}

fn send(svm: &mut LiteSVM, ixs: &[Instruction], signers: &[&Keypair]) -> Result<(), String> {
    svm.expire_blockhash();
    let msg = Message::new_with_blockhash(ixs, Some(&signers[0].pubkey()), &svm.latest_blockhash());
    let tx = VersionedTransaction::try_new(VersionedMessage::Legacy(msg), signers).unwrap();
    svm.send_transaction(tx).map(|_| ()).map_err(|e| e.meta.logs.join("\n"))
}

fn ix(data: impl InstructionData, accounts: impl ToAccountMetas) -> Instruction {
    Instruction::new_with_bytes(reputation::id(), &data.data(), accounts.to_account_metas(None))
}

fn setup() -> Env {
    let mut svm = LiteSVM::new();
    let bytes = include_bytes!(concat!(env!("CARGO_TARGET_TMPDIR"), "/../deploy/reputation.so"));
    svm.add_program(reputation::id(), bytes).unwrap();

    let bank = Keypair::new();
    let wallet = Keypair::new();
    svm.airdrop(&bank.pubkey(), 10_000_000_000).unwrap();
    svm.airdrop(&wallet.pubkey(), 1_000_000_000).unwrap();

    let config = Pubkey::find_program_address(&[CONFIG_SEED, bank.pubkey().as_ref()], &reputation::id()).0;
    let reputation = Pubkey::find_program_address(&[REPUTATION_SEED, config.as_ref(), wallet.pubkey().as_ref()], &reputation::id()).0;
    let sbt_mint = Pubkey::find_program_address(&[SBT_SEED, reputation.as_ref()], &reputation::id()).0;
    let wallet_sbt = get_associated_token_address_with_program_id(&wallet.pubkey(), &sbt_mint, &spl_token_2022::ID);
    let mut env = Env { svm, k: Keys { bank, wallet, config, reputation, sbt_mint, wallet_sbt } };

    let init_config = ix(
        reputation::instruction::InitConfig {},
        reputation::accounts::InitConfig { authority: env.k.bank.pubkey(), config, system_program: system_program::ID },
    );
    let init_rep = ix(
        reputation::instruction::InitReputation {},
        reputation::accounts::InitReputation {
            authority: env.k.bank.pubkey(),
            config,
            wallet: env.k.wallet.pubkey(),
            reputation,
            system_program: system_program::ID,
        },
    );
    send(&mut env.svm, &[init_config, init_rep], &[&env.k.bank]).unwrap();
    env
}

fn record_ix(k: &Keys, authority: Pubkey, on_time: bool) -> Instruction {
    let config = Pubkey::find_program_address(&[CONFIG_SEED, authority.as_ref()], &reputation::id()).0;
    ix(
        reputation::instruction::RecordRepayment { on_time, amount: 359_100_000 },
        reputation::accounts::UpdateReputation { authority, config, reputation: k.reputation },
    )
}

fn default_ix(k: &Keys) -> Instruction {
    ix(
        reputation::instruction::RecordDefault {},
        reputation::accounts::UpdateReputation { authority: k.bank.pubkey(), config: k.config, reputation: k.reputation },
    )
}

fn mint_ix(k: &Keys) -> Instruction {
    ix(
        reputation::instruction::MintCredential { uri: URI.to_string() },
        reputation::accounts::MintCredential {
            authority: k.bank.pubkey(),
            config: k.config,
            reputation: k.reputation,
            wallet: k.wallet.pubkey(),
            sbt_mint: k.sbt_mint,
            wallet_sbt_account: k.wallet_sbt,
            token_program: spl_token_2022::ID,
            associated_token_program: spl_associated_token_account::program::ID,
            system_program: system_program::ID,
        },
    )
}

fn rep(svm: &LiteSVM, address: &Pubkey) -> Reputation {
    Reputation::try_deserialize(&mut svm.get_account(address).unwrap().data.as_slice()).unwrap()
}

#[test]
fn soulbound_credential_is_minted_once_with_metadata_and_cannot_be_transferred() {
    let mut env = setup();
    let bank = env.k.bank.insecure_clone();

    let err = send(&mut env.svm, &[mint_ix(&env.k)], &[&bank]).unwrap_err();
    assert!(err.contains("NotEligible"), "{err}");

    send(&mut env.svm, &[record_ix(&env.k, bank.pubkey(), true), mint_ix(&env.k)], &[&bank]).unwrap();

    let state = rep(&env.svm, &env.k.reputation);
    assert_eq!(state.sbt_mint, env.k.sbt_mint);
    assert_eq!((state.on_time_repayments, state.tier, state.total_repaid), (1, 0, 359_100_000));

    let mint_account = env.svm.get_account(&env.k.sbt_mint).unwrap();
    assert_eq!(mint_account.owner, spl_token_2022::ID);
    let mint = StateWithExtensions::<Mint2022>::unpack(&mint_account.data).unwrap();
    assert_eq!(mint.base.supply, 1);
    assert_eq!(mint.base.decimals, 0);
    assert!(Option::<Pubkey>::from(mint.base.mint_authority).is_none(), "supply must be capped at one");
    mint.get_extension::<NonTransferable>().unwrap();
    let pointer = mint.get_extension::<MetadataPointer>().unwrap();
    assert_eq!(Option::<Pubkey>::from(pointer.metadata_address), Some(env.k.sbt_mint));
    let metadata = mint.get_variable_len_extension::<TokenMetadata>().unwrap();
    assert_eq!((metadata.name.as_str(), metadata.symbol.as_str(), metadata.uri.as_str()), (SBT_NAME, SBT_SYMBOL, URI));
    assert_eq!(Option::<Pubkey>::from(metadata.update_authority), Some(env.k.config));

    let holder_account = env.svm.get_account(&env.k.wallet_sbt).unwrap();
    let holder = StateWithExtensions::<TokenAccount2022>::unpack(&holder_account.data).unwrap();
    assert_eq!((holder.base.owner, holder.base.amount), (env.k.wallet.pubkey(), 1));

    let err = send(&mut env.svm, &[mint_ix(&env.k)], &[&bank]).unwrap_err();
    assert!(err.contains("AlreadyMinted") || err.contains("already in use"), "{err}");

    // NonTransferable: even the holder cannot move the credential.
    let wallet = env.k.wallet.insecure_clone();
    let other = Keypair::new();
    let other_ata = get_associated_token_address_with_program_id(&other.pubkey(), &env.k.sbt_mint, &spl_token_2022::ID);
    let create_other = spl_associated_token_account::instruction::create_associated_token_account(
        &wallet.pubkey(), &other.pubkey(), &env.k.sbt_mint, &spl_token_2022::ID);
    let transfer = spl_token_2022::instruction::transfer_checked(
        &spl_token_2022::ID, &env.k.wallet_sbt, &env.k.sbt_mint, &other_ata, &wallet.pubkey(), &[], 1, 0).unwrap();
    send(&mut env.svm, &[create_other], &[&wallet]).unwrap();
    let err = send(&mut env.svm, &[transfer], &[&wallet]).unwrap_err();
    assert!(err.contains("Transfer is disabled for this mint"), "{err}");
}

#[test]
fn tier_follows_on_time_repayments_and_defaults() {
    let mut env = setup();
    let bank = env.k.bank.insecure_clone();
    let tier = |env: &Env| rep(&env.svm, &env.k.reputation).tier;

    send(&mut env.svm, &[record_ix(&env.k, bank.pubkey(), true)], &[&bank]).unwrap();
    assert_eq!(tier(&env), 0);
    send(&mut env.svm, &[record_ix(&env.k, bank.pubkey(), false)], &[&bank]).unwrap();
    assert_eq!(tier(&env), 0, "late repayments do not count toward tiers");
    send(&mut env.svm, &[record_ix(&env.k, bank.pubkey(), true)], &[&bank]).unwrap();
    assert_eq!(tier(&env), 1);
    for _ in 0..3 {
        send(&mut env.svm, &[record_ix(&env.k, bank.pubkey(), true)], &[&bank]).unwrap();
    }
    assert_eq!(tier(&env), 2);
    send(&mut env.svm, &[default_ix(&env.k)], &[&bank]).unwrap();
    let state = rep(&env.svm, &env.k.reputation);
    assert_eq!((state.on_time_repayments, state.late_repayments, state.defaults, state.tier), (5, 1, 1, 1));
}

#[test]
fn another_authority_cannot_write_this_reputation() {
    let mut env = setup();
    let attacker = Keypair::new();
    env.svm.airdrop(&attacker.pubkey(), 1_000_000_000).unwrap();
    let attacker_config = Pubkey::find_program_address(&[CONFIG_SEED, attacker.pubkey().as_ref()], &reputation::id()).0;
    let own_config = ix(
        reputation::instruction::InitConfig {},
        reputation::accounts::InitConfig { authority: attacker.pubkey(), config: attacker_config, system_program: system_program::ID },
    );
    send(&mut env.svm, &[own_config], &[&attacker]).unwrap();

    assert!(send(&mut env.svm, &[record_ix(&env.k, attacker.pubkey(), true)], &[&attacker]).is_err());
    let mut forged = record_ix(&env.k, attacker.pubkey(), true);
    forged.accounts[1].pubkey = env.k.config;
    assert!(send(&mut env.svm, &[forged], &[&attacker]).is_err());
    assert_eq!(rep(&env.svm, &env.k.reputation).on_time_repayments, 0);
}
