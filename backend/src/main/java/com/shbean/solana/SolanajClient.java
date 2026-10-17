package com.shbean.solana;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.rpc.types.ConfirmedTransaction;
import org.p2p.solanaj.rpc.types.SignatureStatuses;
import org.p2p.solanaj.rpc.types.config.Commitment;
import org.p2p.solanaj.rpc.types.config.RpcSendTransactionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** {@link SolanaClient} backed by solanaj; the only class that may import {@code org.p2p.solanaj}. */
@Component
public class SolanajClient implements SolanaClient {

    private static final int SIGNATURE_LENGTH = 64;
    private static final int MAX_RPC_RETRIES = 5;

    private final RpcApi rpc;
    private final Commitment commitment;

    @Autowired
    public SolanajClient(SolanaProperties props) {
        this(new RpcClient(props.rpcUrl()).getApi(), commitmentOf(props.commitment()));
    }

    SolanajClient(RpcApi rpc, Commitment commitment) {
        this.rpc = rpc;
        this.commitment = commitment;
    }

    @Override
    public Optional<AccountData> getAccount(String address) {
        AccountInfo info = call("getAccount", () -> rpc.getAccountInfo(new PublicKey(address)));
        if (info == null || info.getValue() == null) {
            return Optional.empty();
        }
        AccountInfo.Value v = info.getValue();
        return Optional.of(new AccountData(v.getOwner(), (long) v.getLamports(), v.isExecutable(), info.getDecodedData()));
    }

    @Override
    public long getBalance(String address) {
        return call("getBalance", () -> rpc.getBalance(new PublicKey(address), commitment));
    }

    @Override
    public Pda findProgramAddress(List<byte[]> seeds, String programId) {
        PublicKey.ProgramDerivedAddress pda = call("findProgramAddress",
                () -> PublicKey.findProgramAddress(seeds, new PublicKey(programId)));
        return new Pda(pda.getAddress().toBase58(), pda.getNonce());
    }

    @Override
    public SignedTransaction sign(List<Instruction> instructions, Keypair feePayer, List<Keypair> otherSigners) {
        String blockhash = call("getLatestBlockhash", () -> rpc.getLatestBlockhash(commitment).getValue().getBlockhash());

        Map<String, Account> byAddress = new LinkedHashMap<>();
        for (Keypair k : concat(feePayer, otherSigners)) {
            byAddress.putIfAbsent(k.address(), new Account(k.secretKeyBytes()));
        }
        byte[] wire = build(instructions, blockhash, new ArrayList<>(byAddress.values()));

        // solanaj writes signatures in the order given, but the network expects them in the message's key order.
        List<String> required = requiredSigners(wire);
        if (!required.equals(new ArrayList<>(byAddress.keySet()))) {
            if (!required.getFirst().equals(feePayer.address()) || !Set.copyOf(required).equals(byAddress.keySet())) {
                throw new SolanaException("The provided signers do not match the signers the instructions require");
            }
            wire = build(instructions, blockhash, required.stream().map(byAddress::get).toList());
        }

        // Wire format: shortvec signature count (1 byte for < 128 signers), then the signatures.
        String signature = Base58.encode(Arrays.copyOfRange(wire, 1, 1 + SIGNATURE_LENGTH));
        return new SignedTransaction(signature, blockhash, wire);
    }

    private static byte[] build(List<Instruction> instructions, String blockhash, List<Account> signers) {
        Transaction tx = new Transaction();
        instructions.forEach(i -> tx.addInstruction(toInstruction(i)));
        tx.setRecentBlockHash(blockhash);
        tx.sign(signers);
        return tx.serialize();
    }

    /** Reads the signer keys, in required order, from a serialized transaction (message header + key table). */
    private static List<String> requiredSigners(byte[] wire) {
        int messageStart = 1 + SIGNATURE_LENGTH * (wire[0] & 0xFF);
        int required = wire[messageStart] & 0xFF;
        int keysStart = messageStart + 4;
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < required; i++) {
            keys.add(Base58.encode(Arrays.copyOfRange(wire, keysStart + 32 * i, keysStart + 32 * (i + 1))));
        }
        return keys;
    }

    private static List<Keypair> concat(Keypair first, List<Keypair> rest) {
        List<Keypair> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        return all;
    }

    @Override
    public void submit(SignedTransaction transaction) {
        // solanaj's default is skipPreflight=true, which would let doomed transactions land and fail on-chain.
        RpcSendTransactionConfig config = RpcSendTransactionConfig.builder()
                .encoding(RpcSendTransactionConfig.Encoding.base64)
                .skipPreflight(false)
                .preflightCommitment(commitment.getValue())
                .maxRetries(MAX_RPC_RETRIES)
                .build();
        String returned = call("sendTransaction",
                () -> rpc.sendRawTransaction(Base64.getEncoder().encodeToString(transaction.wire()), config));
        if (!transaction.signature().equals(returned)) {
            throw new SolanaException("Cluster returned an unexpected signature for the submitted transaction");
        }
    }

    @Override
    public TxResult status(String signature) {
        SignatureStatuses statuses = call("getSignatureStatuses", () -> rpc.getSignatureStatuses(List.of(signature), true));
        List<SignatureStatuses.Value> values = statuses == null ? null : statuses.getValue();
        SignatureStatuses.Value seen = values == null || values.isEmpty() ? null : values.getFirst();
        if (seen == null) {
            return new TxResult(signature, Status.NOT_FOUND, null);
        }
        String level = seen.getConfirmationStatus() == null ? "processed" : seen.getConfirmationStatus().toLowerCase(Locale.ROOT);
        if (!level.equals("confirmed") && !level.equals("finalized")) {
            return new TxResult(signature, Status.PROCESSED, null);
        }
        // The status endpoint does not carry the program result, so read it from the transaction itself.
        ConfirmedTransaction landed = call("getTransaction", () -> rpc.getTransaction(signature, Commitment.CONFIRMED));
        if (landed == null || landed.getMeta() == null) {
            return new TxResult(signature, Status.PROCESSED, null);
        }
        if (landed.getMeta().getErr() != null) {
            return new TxResult(signature, Status.FAILED, String.valueOf(landed.getMeta().getErr()));
        }
        return new TxResult(signature, level.equals("finalized") ? Status.FINALIZED : Status.CONFIRMED, null);
    }

    @Override
    public boolean isBlockhashValid(String blockhash) {
        return call("isBlockhashValid", () -> rpc.isBlockhashValid(blockhash, commitment, null));
    }

    private static TransactionInstruction toInstruction(Instruction i) {
        List<org.p2p.solanaj.core.AccountMeta> keys = i.accounts().stream()
                .map(a -> new org.p2p.solanaj.core.AccountMeta(new PublicKey(a.address()), a.signer(), a.writable()))
                .toList();
        return new TransactionInstruction(new PublicKey(i.programId()), keys, i.data());
    }

    private static Commitment commitmentOf(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "processed" -> Commitment.PROCESSED;
            case "confirmed" -> Commitment.CONFIRMED;
            case "finalized" -> Commitment.FINALIZED;
            default -> throw new IllegalArgumentException("solana.commitment must be processed, confirmed or finalized");
        };
    }

    // RpcException text can echo node responses but never our URL or keys; other failures are reduced to their type.
    private static <T> T call(String operation, RpcCall<T> action) {
        try {
            return action.run();
        } catch (RpcException e) {
            throw new SolanaException("Solana " + operation + " failed: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new SolanaException("Solana " + operation + " failed: " + e.getClass().getSimpleName());
        }
    }

    @FunctionalInterface
    private interface RpcCall<T> {
        T run() throws RpcException;
    }
}
