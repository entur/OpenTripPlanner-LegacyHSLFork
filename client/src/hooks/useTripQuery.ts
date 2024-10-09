import { useCallback, useEffect, useState } from 'react';
import { request } from 'graphql-request'; // eslint-disable-line import/no-unresolved
import { Location, QueryType, TripQueryVariables } from '../gql/graphql.ts';
import { getApiUrl } from '../util/getApiUrl.ts';
import { query } from '../static/query/tripQuery.tsx';

/**
  General purpose trip query document for debugging trip searches
 */

type TripQueryHook = (
  variables?: TripQueryVariables,
) => [QueryType | null, boolean, (pageCursor?: string) => Promise<void>];

export const useTripQuery: TripQueryHook = (variables) => {
  const [data, setData] = useState<QueryType | null>(null);
  const [loading, setLoading] = useState(false);
  const callback = useCallback(
    async (pageCursor?: string) => {
      if (loading) {
        console.warn('Wait for previous search to finish');
      } else {
        if (variables) {
          setLoading(true);
          if (pageCursor) {
            setData((await request(getApiUrl(), query, { ...variables, pageCursor })) as QueryType);
          } else {
            setData((await request(getApiUrl(), query, variables)) as QueryType);
          }
          setLoading(false);
        } else {
          console.warn("Can't search without variables");
        }
      }
    },
    [setData, variables, loading],
  );

  useEffect(() => {
    if (validLocation(variables?.from) && validLocation(variables?.to)) {
      callback();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [variables?.from, variables?.to]);
  return [data, loading, callback];
};

function validLocation(location: Location | undefined) {
  return location && (location.coordinates || location.place);
}
