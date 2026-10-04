import {ScoresheetDelivery} from '../domain/match.model';
import {ScoresheetExtraTotals, formatOverExtras} from './scoresheet-ledger';

export function formatInningsTotal(totalRuns: number, totalWickets: number): string {
  return totalWickets >= 10 ? `${totalRuns} ao` : `${totalRuns}`;
}

export function formatCardScore(totalRuns: number, totalWickets: number, declared = false): string {
  const score = totalWickets >= 10
    ? `${totalRuns} ao`
    : totalWickets > 0 ? `${totalRuns}/${totalWickets}` : `${totalRuns}`;
  return declared ? `${score} dec` : score;
}

export function formatOvers(deliveries: readonly ScoresheetDelivery[]): string {
  const legalBalls = deliveries.filter((delivery) => delivery.wides === 0).length;
  return `${Math.floor(legalBalls / 6)}.${legalBalls % 6} ov`;
}

export function formatRunRate(totalRuns: number, deliveries: readonly ScoresheetDelivery[]): string {
  const legalBalls = deliveries.filter((delivery) => delivery.wides === 0).length;
  return legalBalls === 0 ? '—' : (totalRuns * 6 / legalBalls).toFixed(2);
}

export function overNotes(
  deliveries: readonly ScoresheetDelivery[],
  extras: ScoresheetExtraTotals,
  boundaryCount: number,
  dismissedPlayers: ReadonlyMap<number, string>
): string {
  const notes: string[] = [];
  const wicketNotes = deliveries.flatMap((delivery) => {
    const description = wicketDescription(delivery);
    return description
      ? description.split('; ').map((wicket) => `${dismissedPlayers.get(delivery.deliveryKey) || delivery.batter?.trim() || 'Unknown batter'} — ${wicket}`)
      : [];
  });
  if (wicketNotes.length > 0) {
    notes.push(...wicketNotes.map((wicket, index) => `WICKET ${index + 1}: ${wicket}`));
  }
  const formattedExtras = formatOverExtras(extras);
  if (formattedExtras !== '—') {
    notes.push(formattedExtras);
  }
  if (boundaryCount > 0) {
    notes.push(`${boundaryCount} boundary${boundaryCount === 1 ? '' : 'ies'}`);
  }
  return notes.join('\n') || 'No wicket or extra events recorded';
}

export function wicketDescription(delivery: ScoresheetDelivery): string {
  const descriptions = delivery.wickets.map((wicket) => {
    const fielders = wicket.fielders.length > 0 ? ` (${wicket.fielders.join(', ')})` : '';
    const kind = wicket.kind?.trim() || 'Wicket';
    return `${kind.charAt(0).toUpperCase()}${kind.slice(1)}${fielders}`;
  });
  return descriptions.length > 0 ? descriptions.join('; ') : delivery.wicketCount > 0 ? 'Wicket' : '';
}

export function deliverySymbol(delivery: ScoresheetDelivery, includeWicket = true): string {
  if (includeWicket && delivery.wicketCount > 0) {
    return 'W';
  }
  if (delivery.wides > 0) {
    return `${delivery.wides}wd`;
  }
  if (delivery.noBalls > 0) {
    return `${delivery.noBalls}nb`;
  }
  if (delivery.legByes > 0) {
    return `${delivery.legByes}lb`;
  }
  if (delivery.byes > 0) {
    return `${delivery.byes}b`;
  }
  if (delivery.totalRuns === 0) {
    return '•';
  }
  return delivery.totalRuns.toString();
}

export function deliverySymbolClass(delivery: ScoresheetDelivery, includeWicket = true): string {
  if (includeWicket && delivery.wicketCount > 0) {
    return 'matrix-symbol--wicket';
  }
  if (delivery.wides > 0 || delivery.noBalls > 0 || delivery.legByes > 0 || delivery.byes > 0) {
    return 'matrix-symbol--extra';
  }
  if (delivery.batterRuns === 4 || delivery.batterRuns === 6) {
    return 'matrix-symbol--boundary';
  }
  return delivery.totalRuns === 0 ? 'matrix-symbol--dot' : 'matrix-symbol--run';
}

export function formatDismissalSummary(runs: number, balls: number, fours: number, sixes: number): string {
  const boundaries = [
    fours ? `${fours}x4` : '',
    sixes ? `${sixes}x6` : ''
  ].filter(Boolean).join(', ');
  return `(${runs}r, ${balls}b${boundaries ? `, ${boundaries}` : ''})`;
}