import {MatchScoresheetResponse, ScoresheetDelivery} from '../domain/match.model';
import {presentScoresheet} from './scoresheet-presenter';

describe('presentScoresheet', () => {
  it('should prepare grouped rows with cumulative ledger values and separate over extras', () => {
    const response = scoresheet(
      delivery({deliveryKey: 1, overNumber: 1, byes: 3, wides: 1, totalRuns: 4}),
      delivery({deliveryKey: 2, overNumber: 2, legByes: 2, totalRuns: 2})
    );

    const innings = presentScoresheet(response).innings[0];

    expect(innings.rows).toHaveSize(2);
    expect(innings.rows[0].overExtras).toBe('B:3 W:1');
    expect(innings.rows[0].notes).toContain('B:3 W:1');
    expect(innings.rows[0].ledger.extrasDisplay).toBe('B:3 W:1 (4)');
    expect(innings.rows[1].ledger.extrasDisplay).toBe('B:3 LB:2 W:1 (6)');
    expect(innings.rows[1].bowlingLanes[1][0]).toEqual({
      bowler: 'Bowler', overs: '0.1', maidens: 0, runs: 1, wickets: 0, isFirstAppearance: false
    });
  });

  it('should alternate bowlers by end and accumulate their running figures', () => {
    const firstOver = Array.from({length: 6}, (_, index) => delivery({
      deliveryKey: index + 1,
      inningsOrder: index + 1,
      overNumber: 1,
      ballInOver: index + 1,
      bowler: 'Left bowler'
    }));
    const secondOver = Array.from({length: 6}, (_, index) => delivery({
      deliveryKey: index + 7,
      inningsOrder: index + 7,
      overNumber: 2,
      ballInOver: index + 1,
      bowler: 'Right bowler',
      totalRuns: index === 0 ? 4 : 0,
      batterRuns: index === 0 ? 4 : 0,
      wicketCount: index === 5 ? 1 : 0
    }));
    const thirdOver = Array.from({length: 6}, (_, index) => delivery({
      deliveryKey: index + 13,
      inningsOrder: index + 13,
      overNumber: 3,
      ballInOver: index + 1,
      bowler: 'Left bowler',
      totalRuns: 2,
      batterRuns: 2
    }));

    const rows = presentScoresheet(scoresheet(...firstOver, ...secondOver, ...thirdOver)).innings[0].rows;

    expect(rows[0].bowlingLanes[0][0]).toEqual({
      bowler: 'Left bowler', overs: '1', maidens: 1, runs: 0, wickets: 0, isFirstAppearance: true
    });
    expect(rows[0].bowlingLanes[1]).toHaveSize(0);
    expect(rows[1].bowlingLanes[0]).toHaveSize(0);
    expect(rows[1].bowlingLanes[1][0]).toEqual({
      bowler: 'Right bowler', overs: '1', maidens: 0, runs: 4, wickets: 1, isFirstAppearance: true
    });
    expect(rows[2].bowlingLanes[0][0]).toEqual({
      bowler: 'Left bowler', overs: '2', maidens: 1, runs: 12, wickets: 0, isFirstAppearance: false
    });
    expect(rows[2].bowlingLanes[1]).toHaveSize(0);
  });

  it('should place multiple wicket notes on separate lines', () => {
    const firstWicket = delivery({
      deliveryKey: 1,
      wicketCount: 1,
      wickets: [{wicketKey: 1, kind: 'bowled', fielders: []}]
    });
    const secondWicket = delivery({
      deliveryKey: 2,
      inningsOrder: 2,
      ballInOver: 2,
      batter: 'Batter Two',
      wicketCount: 1,
      wickets: [{wicketKey: 2, kind: 'caught', fielders: ['Fielder']}]
    });

    const notes = presentScoresheet(scoresheet(firstWicket, secondWicket)).innings[0].rows[0].notes;

    expect(notes).toBe(
      'WICKET 1: Batter One — Bowled\nWICKET 2: Batter Two — Caught (Fielder)'
    );
  });

  it('should attribute a wicket to the non-striker when the next delivery replaces that player', () => {
    const wicketDelivery = delivery({
      deliveryKey: 88,
      sourceBallId: 16031106,
      inningsOrder: 88,
      overNumber: 13,
      ballNumber: 88,
      ballInOver: 6,
      batter: 'M Kapp',
      nonStriker: 'S Verma',
      batterRuns: 1,
      totalRuns: 1,
      wicketCount: 1,
      wickets: [{wicketKey: 438986, kind: 'run out', fielders: ['EA Burns', 'S Ismail']}]
    });
    const nextDelivery = delivery({
      deliveryKey: 89,
      sourceBallId: 16031107,
      inningsOrder: 89,
      overNumber: 14,
      ballNumber: 89,
      ballInOver: 1,
      batter: 'D Penna',
      nonStriker: 'M Kapp'
    });

    const rows = presentScoresheet(scoresheet(wicketDelivery, nextDelivery)).innings[0].rows;
    const wicketRow = rows[0];
    const notationFor = (player: string) => wicketRow.battingLanes.flat().find((entry) => entry.player === player)!;

    expect(notationFor('M Kapp').dismissed).toBeFalse();
    expect(notationFor('S Verma').dismissed).toBeTrue();
    expect(notationFor('S Verma').scoreLabel).toBe('(0r, 0b)');
    expect(notationFor('M Kapp').symbols[0].value).toBe('1');
    expect(notationFor('M Kapp').symbols[0].className).toBe('matrix-symbol--run');
    expect(notationFor('S Verma').symbols[0].value).toBe('W');
    expect(notationFor('S Verma').symbols[0].className).toBe('matrix-symbol--wicket');
    expect(wicketRow.notes).toContain('WICKET 1: S Verma — Run out (EA Burns, S Ismail)');
    expect(rows[1].battingLanes.flat().map((entry) => entry.player)).toEqual(['M Kapp', 'D Penna']);
  });

  it('should only show dismissal details from the row where the batter is dismissed', () => {
    const earlierDelivery = delivery({
      deliveryKey: 1,
      sourceBallId: 1,
      inningsOrder: 1,
      overNumber: 13,
      ballNumber: 85,
      ballInOver: 1,
      batter: 'M Kapp',
      nonStriker: 'S Verma',
      batterRuns: 1,
      totalRuns: 1
    });
    const dismissal = delivery({
      deliveryKey: 2,
      sourceBallId: 2,
      inningsOrder: 2,
      overNumber: 14,
      ballNumber: 89,
      ballInOver: 1,
      batter: 'M Kapp',
      nonStriker: 'D Penna',
      wicketCount: 1,
      wickets: [{wicketKey: 2, kind: 'caught', fielders: ['Fielder']}]
    });

    const rows = presentScoresheet(scoresheet(earlierDelivery, dismissal)).innings[0].rows;

    expect(rows[0].battingLanes.flat().find((entry) => entry.player === 'M Kapp')).toEqual(jasmine.objectContaining({
      dismissed: false,
      scoreLabel: '(1r, 1b)'
    }));
    expect(rows[1].battingLanes.flat().find((entry) => entry.player === 'M Kapp')).toEqual(jasmine.objectContaining({
      dismissed: true,
      scoreLabel: '(1r, 2b)'
    }));
  });

  it('should prepare striker notation, non-striker placeholders, and first-appearance styling', () => {
    const response = scoresheet(
      delivery({deliveryKey: 1, inningsOrder: 1, overNumber: 1, batter: 'Batter One', nonStriker: 'Batter Two', batterRuns: 4, totalRuns: 4}),
      delivery({deliveryKey: 2, inningsOrder: 2, overNumber: 1, batter: 'Batter One', nonStriker: 'Batter Two', wides: 1, totalRuns: 1}),
      delivery({deliveryKey: 3, inningsOrder: 3, overNumber: 2, batter: 'Batter One', nonStriker: 'Batter Two', batterRuns: 6, totalRuns: 6}),
      delivery({deliveryKey: 4, inningsOrder: 4, overNumber: 2, batter: 'Batter One', nonStriker: 'Batter Two', wicketCount: 1})
    );

    const rows = presentScoresheet(response).innings[0].rows;
    const firstLane = rows[0].battingLanes[0][0];
    const secondLane = rows[0].battingLanes[1][0];

    expect(firstLane.symbols.map((symbol) => symbol.value)).toEqual(['4', '1wd']);
    expect(secondLane.symbols.every((symbol) => symbol.className === 'matrix-symbol--non-striker-placeholder')).toBeTrue();
    expect(secondLane.symbols.every((symbol) => symbol.value === '')).toBeTrue();
    expect(firstLane.isFirstAppearance).toBeTrue();
    expect(secondLane.isFirstAppearance).toBeTrue();
    expect(rows[1].battingLanes[0][0].scoreLabel).toBe('(10r, 3b, 1x4, 1x6)');
    expect(rows[1].battingLanes[0][0].isFirstAppearance).toBeFalse();
    expect(rows[1].battingLanes[0][0].dismissed).toBeTrue();
  });

  it('should mark only the first appearance of each batter', () => {
    const rows = presentScoresheet(scoresheet(
      delivery({deliveryKey: 1, overNumber: 1, batter: 'Batter One', nonStriker: 'Batter Two'}),
      delivery({deliveryKey: 2, inningsOrder: 2, overNumber: 2, batter: 'Batter Two', nonStriker: 'Batter One'}),
      delivery({deliveryKey: 3, inningsOrder: 3, overNumber: 2, batter: 'Batter Two', nonStriker: 'Batter One', wicketCount: 1}),
      delivery({deliveryKey: 4, inningsOrder: 4, overNumber: 3, batter: 'Batter One', nonStriker: 'Batter Three'})
    )).innings[0].rows;

    expect(rows[0].battingLanes[0][0].isFirstAppearance).toBeTrue();
    expect(rows[0].battingLanes[1][0].isFirstAppearance).toBeTrue();
    expect(rows[1].battingLanes[0][0].isFirstAppearance).toBeFalse();
    expect(rows[1].battingLanes[1][0].isFirstAppearance).toBeFalse();
    expect(rows[2].battingLanes[0][0].isFirstAppearance).toBeFalse();
    expect(rows[2].battingLanes[1][0].isFirstAppearance).toBeTrue();
  });

  it('should show cumulative runs and balls for a batter', () => {
    const rows = presentScoresheet(scoresheet(
      delivery({deliveryKey: 1, inningsOrder: 1, overNumber: 1, batterRuns: 2, totalRuns: 2}),
      delivery({deliveryKey: 2, inningsOrder: 2, overNumber: 2, batterRuns: 3, totalRuns: 3})
    )).innings[0].rows;

    expect(rows[0].battingLanes[0][0].scoreLabel).toBe('(2r, 1b)');
    expect(rows[1].battingLanes[0][0].scoreLabel).toBe('(5r, 2b)');
  });

  it('should render counted extra symbols for each delivery', () => {
    const rows = presentScoresheet(scoresheet(
      delivery({deliveryKey: 1, inningsOrder: 1, wides: 1, totalRuns: 1}),
      delivery({deliveryKey: 2, inningsOrder: 2, noBalls: 4, totalRuns: 4}),
      delivery({deliveryKey: 3, inningsOrder: 3, legByes: 2, totalRuns: 2}),
      delivery({deliveryKey: 4, inningsOrder: 4, byes: 3, totalRuns: 3})
    )).innings[0].rows;

    expect(rows[0].battingLanes[0][0].symbols.map((symbol) => symbol.value)).toEqual([
      '1wd', '4nb', '2lb', '3b'
    ]);
    expect(rows[0].battingLanes[0][0].symbols.every((symbol) => symbol.className === 'matrix-symbol--extra')).toBeTrue();
  });

  it('should keep the delivery input immutable while ordering the prepared view', () => {
    const later = delivery({deliveryKey: 2, inningsOrder: 2, overNumber: 2});
    const earlier = delivery({deliveryKey: 1, inningsOrder: 1, overNumber: 1});
    const response = scoresheet(later, earlier);

    const presented = presentScoresheet(response);

    expect(response.innings[0].deliveries).toEqual([later, earlier]);
    expect(presented.innings[0].deliveries).toEqual([earlier, later]);
  });

  it('should mark an innings all out in its prepared total label after ten wickets', () => {
    const deliveries = Array.from({length: 10}, (_, index) => delivery({
      deliveryKey: index + 1,
      inningsOrder: index + 1,
      batterRuns: index === 0 ? 302 : 0,
      totalRuns: index === 0 ? 302 : 0,
      wicketCount: 1
    }));

    const presentedInnings = presentScoresheet(scoresheet(...deliveries)).innings[0];

    expect(presentedInnings.totalWickets).toBe(10);
    expect(presentedInnings.totalLabel).toBe('302 ao');
  });

  it('should prepare compact card metrics using legal deliveries', () => {
    const presentedInnings = presentScoresheet(scoresheet(
      delivery({deliveryKey: 1, wides: 1, totalRuns: 1}),
      delivery({deliveryKey: 2, inningsOrder: 2, batterRuns: 5, totalRuns: 5, wicketCount: 1})
    )).innings[0];

    expect(presentedInnings.cardScoreLabel).toBe('6/1');
    expect(presentedInnings.oversLabel).toBe('0.1 ov');
    expect(presentedInnings.runRateLabel).toBe('36.00');
    expect(presentedInnings.cardFooterLabel).toBe('Innings complete');
  });

  it('should append dec to a declared innings card score', () => {
    const presentedInnings = presentScoresheet(scoresheetWithDeclared(true,
      delivery({deliveryKey: 1, batterRuns: 565, totalRuns: 565, wicketCount: 6}),
    )).innings[0];

    expect(presentedInnings.cardScoreLabel).toBe('565/6 dec');
  });
});

function scoresheet(...deliveries: ScoresheetDelivery[]): MatchScoresheetResponse {
  return scoresheetWithDeclared(false, ...deliveries);
}

function scoresheetWithDeclared(declared: boolean, ...deliveries: ScoresheetDelivery[]): MatchScoresheetResponse {
  return {
    context: {publicMatchId: 1_000_010_101, fileName: 'match.json', team1: 'England', team2: 'India'},
    completeness: 'COMPLETE',
    missingData: [],
    innings: [{inningsNumber: 1, battingTeam: 'England', bowlingTeam: 'India', declared, deliveries}]
  };
}

function delivery(overrides: Partial<ScoresheetDelivery>): ScoresheetDelivery {
  return {
    deliveryKey: 1,
    sourceBallId: 1,
    inningsOrder: 1,
    overNumber: 1,
    ballNumber: 1,
    ballInOver: 1,
    batter: 'Batter One',
    nonStriker: 'Batter Two',
    bowler: 'Bowler',
    batterRuns: 0,
    extraRuns: 0,
    totalRuns: 0,
    noBalls: 0,
    wides: 0,
    byes: 0,
    legByes: 0,
    nonBoundary: null,
    powerplay: 0,
    wicketCount: 0,
    wickets: [],
    ...overrides
  };
}
