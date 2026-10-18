import { useState } from 'react';
import { Link } from 'react-router-dom';
import { gql, useLazyQuery, useMutation, useQuery } from '@apollo/client';
import {
  ShoppingBag,
  Coffee,
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  ExternalLink,
  Sparkles,
  ArrowRight,
  ShieldCheck,
  X,
  CreditCard,
  Zap,
  ReceiptText,
} from 'lucide-react';
import { BeanTile } from '../components/BeanWorld';
import { monthLong, usd } from '../lib/format';
import { PageSkeleton } from './Home';

const STORE_INIT = gql`
  query StoreInit {
    forecast(months: 3) {
      startingBalance
    }
    creditProfile {
      tier
      cap
    }
  }
`;

const PURCHASE_CHECK = gql`
  query StorePurchaseCheck($amount: Float!, $label: String!) {
    purchaseCheck(amount: $amount, label: $label) {
      verdict
      reason
      balanceNow
      gapToday
      affordableFrom
      laterShortfallMonth
      proposal {
        principal
        fee
        repaymentMonth
        totalDue
      }
      explanation {
        text
      }
    }
  }
`;

const PAY_MERCHANT = gql`
  mutation PayMerchant($amount: Float!, $description: String!, $merchantId: String) {
    payMerchant(amount: $amount, description: $description, merchantId: $merchantId)
  }
`;

const ACCEPT_PURCHASE_LOAN = gql`
  mutation StoreAcceptLoan($amount: Float!, $label: String!, $consent: Boolean!, $expectedTotalDue: Float!) {
    acceptPurchaseLoan(amount: $amount, label: $label, consent: $consent, expectedTotalDue: $expectedTotalDue) {
      id
      status
      principal
      fee
      totalDue
      vendorTxUrl
      disburseTxUrl
      purchaseLabel
      purchaseAmount
    }
  }
`;

type Product = {
  id: string;
  name: string;
  price: number;
  description: string;
  badge: string;
  expectedVerdict: 'AFFORDABLE' | 'LOAN_AVAILABLE' | 'NOT_ELIGIBLE';
  icon: typeof Coffee;
};

const PRODUCTS: Product[] = [
  {
    id: 'grinder',
    name: 'Bean Burr Grinder',
    price: 20.0,
    description: 'Precision conical burrs for an exceptionally consistent drip or espresso grind. Fully covered by your checking balance ($300.00).',
    badge: 'Affordable Now',
    expectedVerdict: 'AFFORDABLE',
    icon: Coffee,
  },
  {
    id: 'keg',
    name: 'Cold Brew Draft Keg',
    price: 380.0,
    description: 'Stainless steel nitro draft tap system with dual insulation. Exceeds balance by $80.00 — eligible for an instant Tier 1 micro-loan.',
    badge: 'Micro-Loan Eligible',
    expectedVerdict: 'LOAN_AVAILABLE',
    icon: Zap,
  },
  {
    id: 'espresso-machine',
    name: 'Commercial Espresso Machine',
    price: 900.0,
    description: 'Dual-boiler commercial espresso machine. The $600.00 gap exceeds Tier 1 ($500.00 limit). Unlocked at Tier 2!',
    badge: 'Exceeds Tier 1',
    expectedVerdict: 'NOT_ELIGIBLE',
    icon: ShoppingBag,
  },
  {
    id: 'roaster',
    name: 'Artisan Sample Roaster',
    price: 2500.0,
    description: 'Commercial 5kg drum roaster with digital PID profile monitoring. Exceeds maximum micro-loan credit limits.',
    badge: 'Exceeds Credit Limit',
    expectedVerdict: 'NOT_ELIGIBLE',
    icon: ShoppingBag,
  },
];

type FlowSuccessInfo = {
  message: string;
  productName: string;
  productPrice: number;
  paidFromBalance: number;
  loanPrincipal?: number;
  loanFee?: number;
  repayMonth?: string;
  newBalance: number;
  txUrl?: string | null;
};

export default function Store() {
  const { data: initData, loading: initLoading, refetch: refetchStore } = useQuery(STORE_INIT);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [consent, setConsent] = useState(false);
  const [flowSuccess, setFlowSuccess] = useState<FlowSuccessInfo | null>(null);
  const [flowError, setFlowError] = useState<string | null>(null);

  const [checkPurchase, { data: checkData, loading: checking }] = useLazyQuery(PURCHASE_CHECK, {
    fetchPolicy: 'network-only',
  });

  const [payMerchant, { loading: payingMerchant }] = useMutation(PAY_MERCHANT);
  const [acceptLoan, { loading: acceptingLoan }] = useMutation(ACCEPT_PURCHASE_LOAN);

  if (initLoading && !initData) return <PageSkeleton />;

  const balance = initData?.forecast?.startingBalance ?? 0;
  const creditTier = initData?.creditProfile?.tier ?? 1;
  const creditCap = initData?.creditProfile?.cap ?? 500;

  async function openCheckout(product: Product) {
    setSelectedProduct(product);
    setFlowSuccess(null);
    setFlowError(null);
    setConsent(false);
    await checkPurchase({ variables: { amount: product.price, label: product.name } });
  }

  function closeCheckout() {
    setSelectedProduct(null);
    setFlowSuccess(null);
    setFlowError(null);
    setConsent(false);
  }

  async function handleDirectPayment(product: Product) {
    setFlowError(null);
    try {
      await payMerchant({
        variables: {
          amount: product.price,
          description: product.name,
          merchantId: 'beanmart',
        },
      });
      const newBal = Math.max(0, balance - product.price);
      setFlowSuccess({
        message: `Purchased ${product.name} for ${usd.format(product.price)}! Payment settled to Bean Mart.`,
        productName: product.name,
        productPrice: product.price,
        paidFromBalance: product.price,
        newBalance: newBal,
      });
      await refetchStore();
    } catch (err) {
      setFlowError(err instanceof Error ? err.message : 'Payment failed. Please try again.');
    }
  }

  async function handleLoanCheckout(product: Product) {
    const proposal = checkData?.purchaseCheck?.proposal;
    if (!proposal) return;
    setFlowError(null);
    try {
      const res = await acceptLoan({
        variables: {
          amount: product.price,
          label: product.name,
          consent,
          expectedTotalDue: proposal.totalDue,
        },
      });
      const loan = res.data?.acceptPurchaseLoan;
      const paidFromBalance = Math.max(0, balance);
      const newBal = Math.max(0, balance + proposal.principal - product.price);
      setFlowSuccess({
        message: `Micro-loan accepted and paid to Bean Mart for ${product.name}!`,
        productName: product.name,
        productPrice: product.price,
        paidFromBalance: paidFromBalance,
        loanPrincipal: proposal.principal,
        loanFee: proposal.fee,
        repayMonth: proposal.repaymentMonth,
        newBalance: newBal,
        txUrl: loan?.vendorTxUrl,
      });
      await refetchStore();
    } catch (err) {
      setFlowError(err instanceof Error ? err.message : 'Failed to accept loan and pay vendor.');
    }
  }

  const checkResult = checkData?.purchaseCheck;

  return (
    <div className="page store">
      <div className="page__head">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <BeanTile size={32} />
            <h1 style={{ margin: 0 }}>Bean Mart</h1>
          </div>
          <p className="muted" style={{ margin: '6px 0 0 0' }}>
            The official merchandise & equipment partner for Weenie Beanie members.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '14px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '10px',
              padding: '8px 16px',
              borderRadius: 'var(--radius-sm)',
              background: '#ffffff',
              border: '1.5px solid var(--border)',
              boxShadow: 'var(--shadow-sm)',
            }}
          >
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Balance
            </span>
            <span style={{ fontSize: '1.3rem', fontWeight: 800, color: '#0e2a47' }}>
              {usd.format(balance)}
            </span>
          </div>
          <span
            className="status status--calm"
            style={{ fontSize: '0.9rem', padding: '8px 14px', fontWeight: 600, borderRadius: 'var(--radius-sm)' }}
          >
            Tier {creditTier} · Limit {usd.format(creditCap)}
          </span>
        </div>
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          padding: '12px 16px',
          borderRadius: 'var(--radius-sm)',
          background: 'var(--navy-50)',
          color: 'var(--ink)',
          marginBottom: '24px',
        }}
      >
        <ShieldCheck size={20} color="var(--navy-700)" />
        <span style={{ fontSize: '0.9rem' }}>
          <strong>Connected Merchant:</strong> Bean Mart accepts direct Capital One Nessie payments and Solana SPL token vendor settlement.
        </span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
        {PRODUCTS.map((product) => {
          const Icon = product.icon;
          return (
            <div
              key={product.id}
              className="card"
              style={{
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                padding: '24px',
                position: 'relative',
              }}
            >
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                  <div
                    style={{
                      width: '48px',
                      height: '48px',
                      borderRadius: '12px',
                      background: 'var(--ground)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: 'var(--navy-700)',
                    }}
                  >
                    <Icon size={24} />
                  </div>
                  <span
                    className={`status ${
                      product.expectedVerdict === 'AFFORDABLE'
                        ? 'status--good'
                        : product.expectedVerdict === 'LOAN_AVAILABLE'
                        ? 'status--calm'
                        : 'status--warn'
                    }`}
                  >
                    {product.badge}
                  </span>
                </div>

                <h2 style={{ fontSize: '1.25rem', margin: '0 0 8px 0' }}>{product.name}</h2>
                <p className="figure" style={{ fontSize: '1.75rem', fontWeight: 750, margin: '0 0 12px 0' }}>
                  {usd.format(product.price)}
                </p>
                <p className="muted" style={{ fontSize: '0.9rem', lineHeight: 1.5, margin: '0 0 20px 0' }}>
                  {product.description}
                </p>
              </div>

              <button
                type="button"
                className="button"
                style={{ width: '100%' }}
                onClick={() => void openCheckout(product)}
              >
                Buy or Finance
              </button>
            </div>
          );
        })}
      </div>

      {/* Checkout Modal / Slide-over */}
      {selectedProduct && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-title"
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(19, 33, 47, 0.65)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px',
            zIndex: 100,
          }}
        >
          <div
            className="card"
            style={{
              width: '100%',
              maxWidth: '560px',
              maxHeight: '90vh',
              overflowY: 'auto',
              padding: '28px',
              display: 'grid',
              gap: '20px',
              boxShadow: 'var(--shadow-lift)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <h2 id="modal-title" style={{ margin: 0, fontSize: '1.35rem' }}>
                  Checkout: {selectedProduct.name}
                </h2>
                <p className="figure" style={{ fontSize: '1.5rem', fontWeight: 750, margin: '4px 0 0 0' }}>
                  {usd.format(selectedProduct.price)}
                </p>
              </div>
              <button
                type="button"
                className="icon-button"
                aria-label="Close checkout"
                onClick={closeCheckout}
              >
                <X size={20} />
              </button>
            </div>

            {checking ? (
              <div style={{ padding: '40px', textAlign: 'center' }}>
                <p className="muted">Evaluating account affordability & credit eligibility…</p>
              </div>
            ) : flowSuccess ? (
              <div style={{ display: 'grid', gap: '16px', textAlign: 'center', padding: '16px 0' }}>
                <div style={{ margin: '0 auto', color: 'var(--green-700)' }}>
                  <CheckCircle2 size={52} />
                </div>
                <h3 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 800, color: '#0e2a47' }}>
                  Purchase Complete!
                </h3>
                <p style={{ margin: 0, color: 'var(--ink-2)', fontSize: '0.95rem' }}>
                  {flowSuccess.message}
                </p>

                <div
                  style={{
                    display: 'grid',
                    gap: '10px',
                    padding: '16px',
                    borderRadius: 'var(--radius-sm)',
                    background: '#f8fafc',
                    border: '1.5px solid var(--border)',
                    textAlign: 'left',
                    fontSize: '0.9rem',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 600 }}>
                    <span>Item:</span>
                    <span>{flowSuccess.productName} ({usd.format(flowSuccess.productPrice)})</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--ink-2)' }}>
                    <span>Paid with Checking Funds:</span>
                    <span>{usd.format(flowSuccess.paidFromBalance)}</span>
                  </div>
                  {flowSuccess.loanPrincipal && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--ink-2)' }}>
                      <span>Covered by Micro-Loan:</span>
                      <span>{usd.format(flowSuccess.loanPrincipal)} (fee: {usd.format(flowSuccess.loanFee ?? 0)})</span>
                    </div>
                  )}
                  {flowSuccess.repayMonth && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', color: '#1e40af', fontWeight: 600 }}>
                      <span>Scheduled Auto-Repayment:</span>
                      <span>End of {monthLong(flowSuccess.repayMonth)}</span>
                    </div>
                  )}
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      fontWeight: 800,
                      paddingTop: '8px',
                      borderTop: '1px solid var(--border)',
                      fontSize: '1rem',
                      color: '#0e2a47',
                    }}
                  >
                    <span>Updated Checking Balance:</span>
                    <span>{usd.format(flowSuccess.newBalance)}</span>
                  </div>
                </div>

                {flowSuccess.txUrl && (
                  <p style={{ margin: 0 }}>
                    <a href={flowSuccess.txUrl} target="_blank" rel="noopener noreferrer" className="text-link">
                      View vendor transfer on Solana Explorer <ExternalLink size={14} />
                    </a>
                  </p>
                )}
                <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', marginTop: '8px', flexWrap: 'wrap' }}>
                  <Link
                    to="/expenses?tab=purchases"
                    className="button"
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                    onClick={closeCheckout}
                  >
                    <ReceiptText size={16} />
                    See All Purchases
                  </Link>
                  <Link
                    to="/loans"
                    className="button button--secondary"
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                    onClick={closeCheckout}
                  >
                    Loan Tab
                  </Link>
                  <button type="button" className="button button--secondary" onClick={closeCheckout}>
                    Continue Shopping
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ display: 'grid', gap: '16px' }}>
                {checkResult?.verdict === 'AFFORDABLE' && (
                  <div style={{ display: 'grid', gap: '14px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <CheckCircle2 color="var(--green-700)" size={20} />
                      <span className="status status--good">Affordable with Current Balance</span>
                    </div>
                    <p style={{ margin: 0 }}>
                      You have <strong>{usd.format(checkResult.balanceNow)}</strong> available. This purchase leaves your safety buffer intact.
                    </p>
                    {checkResult.explanation?.text && (
                      <p className="offer__why" style={{ margin: 0 }}>
                        <Sparkles size={16} />
                        <span>{checkResult.explanation.text}</span>
                      </p>
                    )}
                    {checkResult.laterShortfallMonth && (
                      <p className="small muted" style={{ margin: 0, fontSize: '0.85rem' }}>
                        Note: An informational shortfall is projected later in {monthLong(checkResult.laterShortfallMonth)} (outside the 3-month window).
                      </p>
                    )}
                    <button
                      type="button"
                      className="button"
                      disabled={payingMerchant}
                      onClick={() => void handleDirectPayment(selectedProduct)}
                    >
                      <CreditCard size={18} />
                      {payingMerchant ? 'Processing Payment…' : `Pay ${usd.format(selectedProduct.price)} with Account Funds`}
                    </button>
                  </div>
                )}

                {checkResult?.verdict === 'LOAN_AVAILABLE' && checkResult.proposal && (
                  <div style={{ display: 'grid', gap: '14px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <CheckCircle2 color="var(--navy-700)" size={20} />
                      <span className="status status--calm">Eligible for Micro-Loan</span>
                    </div>
                    <p style={{ margin: 0 }}>
                      You have {usd.format(checkResult.balanceNow)}. The gap of {usd.format(checkResult.gapToday)} is funded via an instant micro-loan disbursed directly to Bean Mart.
                    </p>

                    <dl className="terms" style={{ background: 'var(--ground)', padding: '0 12px', borderRadius: 'var(--radius-sm)' }}>
                      <div>
                        <dt>Loan Principal</dt>
                        <dd>{usd.format(checkResult.proposal.principal)}</dd>
                      </div>
                      <div>
                        <dt>Flat Fee</dt>
                        <dd>{usd.format(checkResult.proposal.fee)}</dd>
                      </div>
                      <div>
                        <dt>Repayment</dt>
                        <dd>
                          {usd.format(checkResult.proposal.totalDue)} in {monthLong(checkResult.proposal.repaymentMonth)}
                        </dd>
                      </div>
                    </dl>

                    {checkResult.explanation?.text && (
                      <p className="offer__why" style={{ margin: 0 }}>
                        <Sparkles size={16} />
                        <span>{checkResult.explanation.text}</span>
                      </p>
                    )}

                    <label className="consent">
                      <input
                        type="checkbox"
                        checked={consent}
                        onChange={(e) => setConsent(e.target.checked)}
                      />
                      <span>
                        I agree to receive {usd.format(checkResult.proposal.principal)} and authorize an automatic repayment of{' '}
                        {usd.format(checkResult.proposal.totalDue)} at the end of {monthLong(checkResult.proposal.repaymentMonth)}.
                      </span>
                    </label>

                    <button
                      type="button"
                      className="button"
                      disabled={!consent || acceptingLoan}
                      onClick={() => void handleLoanCheckout(selectedProduct)}
                    >
                      {acceptingLoan ? 'Financing & Settling…' : `Finance & Pay Vendor (${usd.format(selectedProduct.price)})`}
                    </button>
                  </div>
                )}

                {checkResult?.verdict === 'NOT_ELIGIBLE' && (
                  <div style={{ display: 'grid', gap: '14px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <AlertTriangle color="var(--red)" size={20} />
                      <span className="status status--bad">Not Eligible for Financing</span>
                    </div>

                    {selectedProduct.price > checkResult.balanceNow && (
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '10px',
                          padding: '12px 14px',
                          borderRadius: 'var(--radius-sm)',
                          background: '#fff1f2',
                          border: '1.5px solid #fecdd3',
                          color: '#9f1239',
                          fontSize: '0.9rem',
                        }}
                      >
                        <AlertCircle size={20} style={{ flexShrink: 0 }} />
                        <span>
                          <strong>Exceeds Current Balance:</strong> Price of {usd.format(selectedProduct.price)} exceeds your available checking balance of {usd.format(checkResult.balanceNow)} by <strong>{usd.format(checkResult.gapToday)}</strong>.
                        </span>
                      </div>
                    )}

                    <p style={{ margin: 0, fontWeight: 500 }}>
                      {checkResult.reason === 'EXCEEDS_TIER_CAP'
                        ? `The required micro-loan exceeds your Tier ${creditTier} credit limit of ${usd.format(creditCap)}.`
                        : checkResult.reason === 'HAS_OPEN_LOAN'
                        ? 'You already have an active loan open. Repay it before financing a new purchase.'
                        : checkResult.reason === 'NO_REPAYMENT_MONTH'
                        ? 'Your projected cashflow has no safe month within the 3-month window that can repay this balance gap.'
                        : 'This purchase cannot be safely financed at this time.'}
                    </p>

                    {checkResult.affordableFrom && (
                      <p className="muted" style={{ margin: 0, fontSize: '0.9rem' }}>
                        Based on your income and recurring expenses, you are projected to afford this starting in{' '}
                        <strong>{monthLong(checkResult.affordableFrom)}</strong>.
                      </p>
                    )}

                    {checkResult.explanation?.text && (
                      <p className="offer__why" style={{ margin: 0 }}>
                        <Sparkles size={16} />
                        <span>{checkResult.explanation.text}</span>
                      </p>
                    )}

                    <p className="muted" style={{ margin: 0, fontSize: '0.9rem' }}>
                      Build your credit history by making on-time repayments to level up your beanstalk and unlock higher limits.
                    </p>
                    <Link to="/beanstalk" className="button button--secondary" onClick={closeCheckout}>
                      View Beanstalk Growth <ArrowRight size={16} />
                    </Link>
                  </div>
                )}

                {flowError && (
                  <p className="form-error" role="alert" style={{ margin: 0 }}>
                    {flowError}
                  </p>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
