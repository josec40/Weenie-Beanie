import { ApolloClient, HttpLink, InMemoryCache, from, split } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';
import { onError } from '@apollo/client/link/error';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { getMainDefinition } from '@apollo/client/utilities';
import { createClient, type Client } from 'graphql-ws';
import { clearToken, getToken } from './auth';

export type LiveState = 'connecting' | 'live' | 'reconnecting';
const liveListeners = new Set<(s: LiveState) => void>();
let liveState: LiveState = 'connecting';

function setLiveState(s: LiveState) {
  liveState = s;
  liveListeners.forEach((l) => l(s));
}

export function onLiveState(listener: (s: LiveState) => void): () => void {
  liveListeners.add(listener);
  listener(liveState);
  return () => {
    liveListeners.delete(listener);
  };
}

const authLink = setContext((_, { headers }) => {
  const token = getToken();
  return { headers: { ...headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) } };
});

const errorLink = onError(({ networkError }) => {
  if (networkError && 'statusCode' in networkError && networkError.statusCode === 401) {
    clearToken();
    window.location.assign('/login');
  }
});

const apiBase = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || '';
const wsUrl =
  import.meta.env.VITE_WS_URL ||
  (apiBase
    ? `${apiBase.replace(/^http/, 'ws')}/graphql-ws`
    : `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/graphql-ws`);

// The JWT travels in connection_init: browsers cannot set headers on a WebSocket upgrade.
export const wsClient: Client = createClient({
  url: wsUrl,
  lazy: true,
  retryAttempts: Infinity,
  shouldRetry: () => Boolean(getToken()),
  connectionParams: () => ({ Authorization: `Bearer ${getToken() ?? ''}` }),
  on: {
    connected: () => setLiveState('live'),
    closed: () => setLiveState('reconnecting'),
  },
});

const httpLink = from([errorLink, authLink, new HttpLink({ uri: `${apiBase}/graphql` })]);

export const apolloClient = new ApolloClient({
  link: split(
    ({ query }) => {
      const def = getMainDefinition(query);
      return def.kind === 'OperationDefinition' && def.operation === 'subscription';
    },
    new GraphQLWsLink(wsClient),
    httpLink,
  ),
  cache: new InMemoryCache({
    // Singleton per-user objects without ids: merge instead of replacing when queries select different fields.
    typePolicies: {
      Query: {
        fields: {
          credential: { merge: true },
          creditProfile: { merge: true },
          demoStatus: { merge: true },
          me: { merge: true },
        },
      },
    },
  }),
});

export async function signOut(): Promise<void> {
  clearToken();
  await wsClient.dispose();
  await apolloClient.clearStore();
}
