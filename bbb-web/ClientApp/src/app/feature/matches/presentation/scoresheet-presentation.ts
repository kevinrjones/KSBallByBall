import {MatchScoresheetResponse, ScoresheetDelivery, ScoresheetInnings} from '../domain/match.model';
import {
  addBattingScores,
  battingLaneAssignments,
  battingLanes,
  battingScoresAtDismissal,
  BattingLaneAssignment,
  BattingScore,
  dismissedPlayersByDelivery,
  ScoresheetPlayerNotation
} from './scoresheet-batting-lanes';
import {BowlerFigures, bowlingLanes, ScoresheetBowlerSummary} from './scoresheet-bowling';
import {
  formatCardScore,
  formatInningsTotal,
  formatOvers,
  formatRunRate,
  overNotes
} from './scoresheet-formatting';
import {
  addToLedger,
  emptyLedger,
  extraTotals,
  formatOverExtras,
  ScoresheetLedger
} from './scoresheet-ledger';

export interface ScoresheetOverRow {
  readonly overNumber: number;
  readonly deliveries: readonly ScoresheetDelivery[];
  readonly totalRuns: number;
  readonly wicketCount: number;
  readonly boundaryCount: number;
  readonly overExtras: string;
  readonly bowlingLanes: readonly [
    readonly ScoresheetBowlerSummary[],
    readonly ScoresheetBowlerSummary[]
  ];
  readonly battingLanes: readonly [
    readonly ScoresheetPlayerNotation[],
    readonly ScoresheetPlayerNotation[]
  ];
  readonly notes: string;
  readonly ledger: ScoresheetLedger;
}

export interface PresentedScoresheetInnings extends ScoresheetInnings {
  readonly rows: readonly ScoresheetOverRow[];
  readonly totalRuns: number;
  readonly totalWickets: number;
  readonly totalLabel: string;
  readonly cardScoreLabel: string;
  readonly oversLabel: string;
  readonly runRateLabel: string;
  readonly cardFooterLabel: string;
}

export interface PresentedScoresheet extends Omit<MatchScoresheetResponse, 'innings'> {
  readonly innings: readonly PresentedScoresheetInnings[];
  readonly totalDeliveries: number;
}

interface OverAccumulator {
  readonly totalRuns: number;
  readonly wicketCount: number;
  readonly boundaryCount: number;
}

/**
 * The warehouse contract associates a wicket with a delivery, not a separate
 * dismissed-player field. The following delivery is used to identify which
 * member of the previous batting pair was replaced, with the delivery batter
 * as the fallback when that evidence is unavailable or ambiguous.
 */
export function presentScoresheet(response: MatchScoresheetResponse): PresentedScoresheet {
  const innings = response.innings.map(presentInnings);
  return {
    context: response.context,
    completeness: response.completeness,
    missingData: response.missingData,
    innings,
    totalDeliveries: innings.reduce((total, current) => total + current.deliveries.length, 0)
  };
}

function presentInnings(innings: ScoresheetInnings): PresentedScoresheetInnings {
  const deliveries = orderDeliveries(innings.deliveries);
  const dismissedPlayers = dismissedPlayersByDelivery(deliveries);
  const assignments = battingLaneAssignments(deliveries, dismissedPlayers);
  const dismissalScores = battingScoresAtDismissal(deliveries, dismissedPlayers);
  const rows = buildOverRows(deliveries, assignments, dismissalScores, dismissedPlayers);
  const totalRuns = deliveries.reduce((total, delivery) => total + delivery.totalRuns, 0);
  const totalWickets = deliveries.reduce((total, delivery) => total + delivery.wicketCount, 0);

  return {
    ...innings,
    deliveries,
    rows,
    totalRuns,
    totalWickets,
    totalLabel: formatInningsTotal(totalRuns, totalWickets),
    cardScoreLabel: formatCardScore(totalRuns, totalWickets, innings.declared),
    oversLabel: formatOvers(deliveries),
    runRateLabel: formatRunRate(totalRuns, deliveries),
    cardFooterLabel: totalWickets >= 10 ? 'All Out' : 'Innings complete'
  };
}

function orderDeliveries(deliveries: readonly ScoresheetDelivery[]): ScoresheetDelivery[] {
  return [...deliveries].sort((left, right) =>
    left.inningsOrder - right.inningsOrder || left.deliveryKey - right.deliveryKey
  );
}

function buildOverRows(
  deliveries: readonly ScoresheetDelivery[],
  assignments: ReadonlyMap<number, readonly BattingLaneAssignment[]>,
  dismissalScores: ReadonlyMap<string, BattingScore>,
  dismissedPlayers: ReadonlyMap<number, string>
): ScoresheetOverRow[] {
  const overs = groupDeliveriesByOver(deliveries);
  const displayedPlayers = new Set<string>();
  const battingScores = new Map<string, BattingScore>();
  const displayedBowlers = new Set<string>();
  const bowlerFigures = new Map<string, BowlerFigures>();
  let ledger = emptyLedger();

  return overs.map(({overNumber, deliveries: overDeliveries}) => {
    const over = summarizeOver(overDeliveries);
    const overExtras = extraTotals(overDeliveries);
    ledger = addToLedger(ledger, over.totalRuns, over.wicketCount, overExtras);
    addBattingScores(battingScores, overDeliveries);
    return {
      overNumber,
      deliveries: overDeliveries,
      totalRuns: over.totalRuns,
      wicketCount: over.wicketCount,
      boundaryCount: over.boundaryCount,
      overExtras: formatOverExtras(overExtras),
      bowlingLanes: bowlingLanes(overDeliveries, overNumber, bowlerFigures, displayedBowlers),
      battingLanes: battingLanes(overDeliveries, assignments, battingScores, dismissalScores, displayedPlayers, dismissedPlayers),
      notes: overNotes(overDeliveries, overExtras, over.boundaryCount, dismissedPlayers),
      ledger
    };
  });
}

function groupDeliveriesByOver(deliveries: readonly ScoresheetDelivery[]): Array<{
  readonly overNumber: number;
  readonly deliveries: ScoresheetDelivery[];
}> {
  const deliveriesByOver = new Map<number, ScoresheetDelivery[]>();
  deliveries.forEach((delivery) => {
    const overDeliveries = deliveriesByOver.get(delivery.overNumber) || [];
    overDeliveries.push(delivery);
    deliveriesByOver.set(delivery.overNumber, overDeliveries);
  });
  return Array.from(deliveriesByOver.entries())
    .sort(([left], [right]) => left - right)
    .map(([overNumber, overDeliveries]) => ({overNumber, deliveries: overDeliveries}));
}

function summarizeOver(deliveries: readonly ScoresheetDelivery[]): OverAccumulator {
  return {
    totalRuns: deliveries.reduce((total, delivery) => total + delivery.totalRuns, 0),
    wicketCount: deliveries.reduce((total, delivery) => total + delivery.wicketCount, 0),
    boundaryCount: deliveries.filter((delivery) => delivery.batterRuns === 4 || delivery.batterRuns === 6).length
  };
}