import { useCallback, useEffect, useRef, useState } from 'react';
import { gql, useApolloClient, useSubscription } from '@apollo/client';
import { onLiveState, type LiveState } from '../apollo';

const LOAN_UPDATED = gql`
  subscription LoanUpdated {
    loanUpdated {
      id
      status
    }
  }
`;
const OFFER_CREATED = gql`
  subscription OfferCreated {
    offerCreated {
      id
    }
  }
`;
const CREDENTIAL_UPDATED = gql`
  subscription CredentialUpdated {
    credentialUpdated {
      tier
      onTimeRepayments
    }
  }
`;
const BALANCE_UPDATED = gql`
  subscription BalanceUpdated {
    balanceUpdated {
      balance
      asOf
    }
  }
`;
const NOTIFICATION_ADDED = gql`
  subscription NotificationAdded {
    notificationAdded {
      id
      type
      title
      body
      link
      createdAt
      read
    }
  }
`;

export type LiveNotification = {
  id: string;
  type: string;
  title: string;
  body: string;
  link: string | null;
  createdAt: string;
  read: boolean;
};

/**
 * Subscribes to the user's live streams and refetches the visible queries when something changes; the server
 * pushes ids, the screens keep owning their own queries. Returns the connection state for a quiet indicator.
 */
export function useLiveUpdates(onNotification: (n: LiveNotification) => void): LiveState {
  const client = useApolloClient();
  const timer = useRef<number | undefined>(undefined);
  const [state, setState] = useState<LiveState>('connecting');

  const refresh = useCallback(() => {
    window.clearTimeout(timer.current);
    // Lifecycle events arrive in bursts (accepted → disbursing → disbursed); refetch once per burst.
    timer.current = window.setTimeout(() => void client.refetchQueries({ include: 'active' }), 350);
  }, [client]);

  useEffect(() => onLiveState((s) => {
    setState((prev) => {
      if (prev === 'reconnecting' && s === 'live') refresh();
      return s;
    });
  }), [refresh]);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  useSubscription(LOAN_UPDATED, { onData: refresh });
  useSubscription(OFFER_CREATED, { onData: refresh });
  useSubscription(CREDENTIAL_UPDATED, { onData: refresh });
  useSubscription(BALANCE_UPDATED, { onData: refresh });
  useSubscription<{ notificationAdded: LiveNotification }>(NOTIFICATION_ADDED, {
    onData: ({ data }) => {
      if (data.data) onNotification(data.data.notificationAdded);
      refresh();
    },
  });

  return state;
}
