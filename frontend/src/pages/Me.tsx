import { gql, useApolloClient, useQuery } from '@apollo/client';
import { useNavigate } from 'react-router-dom';
import { clearToken } from '../auth';

const ME = gql`
  query Me {
    me {
      id
      email
      nessieCustomerId
      nessieCheckingAccountId
    }
  }
`;

type MeData = {
  me: {
    id: string;
    email: string;
    nessieCustomerId: string | null;
    nessieCheckingAccountId: string | null;
  } | null;
};

export default function Me() {
  const navigate = useNavigate();
  const client = useApolloClient();
  const { data, loading, error } = useQuery<MeData>(ME);

  async function logout() {
    clearToken();
    await client.clearStore();
    navigate('/login', { replace: true });
  }

  if (loading) return <main className="card">Loading…</main>;
  if (error) return <main className="card error">Could not load your profile.</main>;

  const me = data?.me;
  return (
    <main className="card">
      <h1>Welcome{me ? `, ${me.email}` : ''}</h1>
      <dl>
        <dt>Nessie customer</dt>
        <dd>{me?.nessieCustomerId ?? 'Not linked yet'}</dd>
        <dt>Checking account</dt>
        <dd>{me?.nessieCheckingAccountId ?? 'Not linked yet'}</dd>
      </dl>
      <button onClick={logout}>Sign out</button>
    </main>
  );
}
