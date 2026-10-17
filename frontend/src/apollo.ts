import { ApolloClient, HttpLink, InMemoryCache, from } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';
import { onError } from '@apollo/client/link/error';
import { clearToken, getToken } from './auth';

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

export const apolloClient = new ApolloClient({
  link: from([errorLink, authLink, new HttpLink({ uri: '/graphql' })]),
  cache: new InMemoryCache(),
});
