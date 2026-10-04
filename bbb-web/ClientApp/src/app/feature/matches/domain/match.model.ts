export interface MatchSummary {
  publicMatchId: number;
  fileName: string;
  matchType: string;
  season: string;
  competition?: string;
  date?: string;
  team1?: string;
  score1?: string;
  overs1?: string;
  isTeam1Winner?: boolean;
  team2?: string;
  score2?: string;
  overs2?: string;
  isTeam2Winner?: boolean;
  result?: string;
  format?: string;
}

export interface RecentMatchesResponse {
  matches: MatchSummary[];
}

export type Venue = 0 | 1 | 2;
export type MatchType = 'all' | 't' | 'o' | 'itt' | 'f' | 'a' | 'tt' | 'wt' | 'wo' | 'witt' | 'wf' | 'wa' | 'wtt' | 'sec';
export type MatchResult = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11;

export interface MatchSearchFilters {
  readonly team: string;
  readonly teamExactMatch: boolean;
  readonly opponents: string;
  readonly opponentsExactMatch: boolean;
  readonly venue: Venue;
  readonly startDate: string;
  readonly endDate: string;
  readonly matchType: MatchType;
  readonly matchResult: MatchResult;
}

export interface MatchSearchQuery extends MatchSearchFilters {
  readonly page: number;
  readonly pageSize: number;
}

export interface MatchSearchResult {
  readonly publicMatchId: number;
  readonly fileName: string;
  readonly matchType?: string | null;
  readonly season?: string | null;
  readonly competition?: string | null;
  readonly date?: string | null;
  readonly team1?: string | null;
  readonly team2?: string | null;
  readonly ground?: string | null;
  readonly result?: string | null;
}

export interface MatchSearchPagination {
  readonly page: number;
  readonly pageSize: number;
  readonly totalResults: number;
  readonly hasNext: boolean;
  readonly nextPage?: number | null;
}

export interface MatchSearchResponse {
  readonly matches: MatchSearchResult[];
  readonly pagination: MatchSearchPagination;
}

export type ScoresheetCompleteness = 'COMPLETE' | 'INCOMPLETE' | 'EMPTY';

export interface ScoresheetWicket {
  readonly wicketKey: number;
  readonly kind?: string | null;
  readonly fielders: readonly string[];
}

export interface ScoresheetDelivery {
  readonly deliveryKey: number;
  readonly sourceBallId: number;
  readonly inningsOrder: number;
  readonly overNumber: number;
  readonly ballNumber: number;
  readonly ballInOver: number;
  readonly batter?: string | null;
  readonly nonStriker?: string | null;
  readonly bowler?: string | null;
  readonly batterRuns: number;
  readonly extraRuns: number;
  readonly totalRuns: number;
  readonly noBalls: number;
  readonly wides: number;
  readonly byes: number;
  readonly legByes: number;
  readonly nonBoundary?: number | null;
  readonly powerplay: number;
  readonly wicketCount: number;
  readonly wickets: readonly ScoresheetWicket[];
}

export interface ScoresheetInnings {
  readonly inningsNumber: number;
  readonly battingTeam?: string | null;
  readonly bowlingTeam?: string | null;
  readonly declared: boolean;
  readonly deliveries: readonly ScoresheetDelivery[];
}

export interface MatchScoresheetContext {
  readonly publicMatchId: number;
  readonly fileName: string;
  readonly matchType?: string | null;
  readonly season?: string | null;
  readonly competition?: string | null;
  readonly date?: string | null;
  readonly team1?: string | null;
  readonly team2?: string | null;
  readonly ground?: string | null;
  readonly result?: string | null;
}

export interface MatchScoresheetResponse {
  readonly context: MatchScoresheetContext;
  readonly completeness: ScoresheetCompleteness;
  readonly missingData: readonly string[];
  readonly innings: readonly ScoresheetInnings[];
}

export interface ApiError {
  code: string;
  message: string;
}
