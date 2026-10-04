import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap} from '@angular/router';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {MatchService} from '../data/match.service';
import {SelectedMatchPlaceholderComponent} from './selected-match-placeholder.component';
import {MatchScoresheetResponse, ScoresheetDelivery, ScoresheetInnings} from '../domain/match.model';

describe('SelectedMatchPlaceholderComponent', () => {
  it('should preserve the complete valid search query when returning to results', async () => {
    await configure({
      team: 'India', teamExactMatch: 'true', opponents: 'Pakistan', opponentsExactMatch: 'false',
      venue: '0', startDate: '2023-01-01', endDate: '2023-12-31', matchType: 'itt', matchResult: '3',
      page: '2', pageSize: '10'
    });
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.returnQueryParams()).toEqual({
      team: 'India', teamExactMatch: 'true', opponents: 'Pakistan', opponentsExactMatch: 'false',
      venue: '0', startDate: '2023-01-01', endDate: '2023-12-31', matchType: 'itt', matchResult: '3',
      page: '2', pageSize: '10'
    });
  });

  it('should reject a malformed public match ID', async () => {
    await configure({}, 'not-a-key');
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.isValidPublicMatchId()).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('selected public match ID is invalid');
  });

  it('should reload when Angular reuses the component for a new public match ID', async () => {
    const routeParams = new BehaviorSubject(convertToParamMap({publicMatchId: '1000010101'}));
    const getScoresheet = jasmine.createSpy().and.returnValue(of({result: scoresheet()}));
    await configure({}, '1000010101', scoresheet(), routeParams.asObservable(), getScoresheet);
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    routeParams.next(convertToParamMap({publicMatchId: '1000010102'}));
    fixture.detectChanges();

    expect(getScoresheet.calls.allArgs()).toEqual([[1000010101], [1000010102]]);
    expect(fixture.componentInstance.publicMatchId()).toBe('1000010102');
  });

  it('should render deliveries as a grouped linear over-by-over matrix', async () => {
    const firstDelivery = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 4, totalRuns: 4
    });
    const secondDelivery = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 0, totalRuns: 0, wicketCount: 1,
      wickets: [{wicketKey: 11, kind: 'Bowled', fielders: []}]
    });
    await configure({}, '1000010101', scoresheet(firstDelivery, secondDelivery));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.linear-matrix')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Source ball');
    expect(fixture.nativeElement.textContent).toContain('Batters linear notation');
    expect(fixture.nativeElement.textContent).toContain('Batter One');
    expect(fixture.nativeElement.textContent).toContain('4');
    expect(fixture.nativeElement.textContent).toContain('Wicket');
    expect(fixture.nativeElement.textContent).toContain('4/1');
    expect(fixture.componentInstance.scoresheet()!.innings[0].rows).toHaveSize(1);
  });

  it('should display zero-based delivery overs starting at one', async () => {
    const firstOver = delivery({deliveryKey: 1, sourceBallId: 101, overNumber: 0, ballInOver: 1});
    const secondOver = delivery({deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 1});
    await configure({}, '1000010101', scoresheet(firstOver, secondOver));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const overLabels = fixture.nativeElement.querySelectorAll('.linear-matrix__over') as NodeListOf<HTMLElement>;

    expect(Array.from(overLabels).map((label) => label.textContent?.trim())).toEqual(['1', '2']);
  });

  it('should show one summary card per innings and only the selected innings panel', async () => {
    const firstInnings = innings(1, 'England', 'India', delivery({deliveryKey: 1, batter: 'England batter', batterRuns: 302, totalRuns: 302}));
    const secondInnings = innings(2, 'India', 'England', delivery({deliveryKey: 2, batter: 'India batter', batterRuns: 150, totalRuns: 150}));
    const thirdInnings = innings(3, 'England', 'India', delivery({deliveryKey: 3, batter: 'England batter two', batterRuns: 75, totalRuns: 75}));
    await configure({}, '1000010101', scoresheetWithInnings(firstInnings, secondInnings, thirdInnings));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('.scoresheet-innings-card') as NodeListOf<HTMLButtonElement>;
    expect(cards).toHaveSize(3);
    expect(Array.from(cards).map((card) => `${card.querySelector('.scoresheet-innings-card__identity')?.textContent?.trim()} ${card.querySelector('.scoresheet-innings-card__summary strong')?.textContent?.trim()}`)).toEqual([
      'England 1st 302', 'India 2nd 150', 'England 3rd 75'
    ]);
    expect(cards[0].getAttribute('aria-label')).toBe('View England 1st, 302');
    expect(cards[0].getAttribute('aria-pressed')).toBe('true');
    expect(cards[1].getAttribute('aria-pressed')).toBe('false');
    expect(fixture.nativeElement.querySelectorAll('.scoresheet-innings')).toHaveSize(1);
    expect(fixture.nativeElement.querySelector('.scoresheet-innings')?.textContent).toContain('England batter');

    cards[1].click();
    fixture.detectChanges();

    expect(cards[0].getAttribute('aria-pressed')).toBe('false');
    expect(cards[1].getAttribute('aria-pressed')).toBe('true');
    expect(fixture.nativeElement.querySelector('.scoresheet-innings')?.textContent).toContain('India batter');
    expect(fixture.nativeElement.querySelector('.scoresheet-innings')?.textContent).not.toContain('England batter two');

    const rightArrow = new KeyboardEvent('keydown', {bubbles: true, key: 'ArrowRight'});
    cards[1].dispatchEvent(rightArrow);
    fixture.detectChanges();

    expect(cards[2].getAttribute('aria-pressed')).toBe('true');
    expect(document.activeElement).toBe(cards[2]);
  });

  it('should display plain inline batter names with placeholders and fade repeated names', async () => {
    const deliveryOne = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 4, totalRuns: 4
    });
    const deliveryTwo = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 1, totalRuns: 1
    });
    const deliveryThree = delivery({
      deliveryKey: 3, sourceBallId: 103, overNumber: 2, ballInOver: 1, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 2, totalRuns: 2
    });
    await configure({}, '1000010101', scoresheet(deliveryOne, deliveryTwo, deliveryThree));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const batterCells = fixture.nativeElement.querySelectorAll('.linear-matrix__batter-cell') as NodeListOf<HTMLElement>;
    const rows = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr') as NodeListOf<HTMLTableRowElement>;
    const strikerCell = batterCells[0];
    const nonStrikerCell = batterCells[1];

    expect(strikerCell.classList).toContain('linear-matrix__batter-cell');
    expect(nonStrikerCell.classList).toContain('linear-matrix__batter-cell');
    expect(strikerCell.querySelectorAll('.matrix-symbol')).toHaveSize(2);
    expect(nonStrikerCell.textContent).toContain('Batter Two');
    expect(nonStrikerCell.querySelectorAll('.matrix-symbol')).toHaveSize(2);
    expect(nonStrikerCell.querySelectorAll('.matrix-symbol--non-striker-placeholder')).toHaveSize(2);
    expect(Array.from(nonStrikerCell.querySelectorAll('.matrix-symbol--non-striker-placeholder'))
      .every((symbol) => symbol.textContent === '')).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('.matrix-player-line')).toHaveSize(4);
    expect(strikerCell.querySelectorAll('.matrix-player-line')).toHaveSize(1);
    expect(nonStrikerCell.querySelectorAll('.matrix-player-line')).toHaveSize(1);
    expect(fixture.nativeElement.querySelectorAll('.matrix-player-header')).toHaveSize(4);
    expect(fixture.nativeElement.querySelectorAll('.matrix-player-header--repeated')).toHaveSize(0);
    const strikerLine = strikerCell.querySelector('.matrix-player-line') as HTMLElement;
    const playerHeader = strikerLine.querySelector('.matrix-player-header') as HTMLElement;
    expect(playerHeader.tagName).toBe('SPAN');
    expect(playerHeader.textContent).toBe('Batter One:');
    expect(playerHeader.nextElementSibling?.classList).toContain('matrix-symbols');
    expect(rows[1].querySelector('.linear-matrix__batter-cell')?.textContent).toContain('(7r, 3b)');
  });

  it('should place the batter score after the last ball before trailing placeholders', async () => {
    const facedBall = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 1, totalRuns: 1
    });
    const nonStrikerBall = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'Batter Two',
      nonStriker: 'Batter One', batterRuns: 3, totalRuns: 3
    });
    await configure({}, '1000010101', scoresheet(facedBall, nonStrikerBall));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const batterLine = fixture.nativeElement.querySelector('.linear-matrix__batter-cell .matrix-player-line') as HTMLElement;
    const notation = batterLine.querySelector('.matrix-symbols') as HTMLElement;
    const score = batterLine.querySelector('.matrix-player-score') as HTMLElement;

    expect(score.parentElement).toBe(notation);
    expect(score.previousElementSibling?.classList).toContain('matrix-symbol--run');
    expect(score.nextElementSibling?.classList).toContain('matrix-symbol--non-striker-placeholder');
  });

  it('should display bowlers in alternating end columns with cumulative figures', async () => {
    const leftOver = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, bowler: 'Left bowler', totalRuns: 1, batterRuns: 1
    });
    const rightOver = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 2, bowler: 'Right bowler', totalRuns: 2, batterRuns: 2
    });
    await configure({}, '1000010101', scoresheet(leftOver, rightOver));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const headers = fixture.nativeElement.querySelectorAll('.linear-matrix__sub-row th') as NodeListOf<HTMLElement>;
    const rows = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr') as NodeListOf<HTMLTableRowElement>;
    const bowlerCells = (row: HTMLTableRowElement): NodeListOf<HTMLElement> =>
      row.querySelectorAll('.linear-matrix__bowler-cell');

    expect(headers[0].textContent?.trim()).toBe('Left');
    expect(headers[1].textContent?.trim()).toBe('Right');
    expect(bowlerCells(rows[0])[0].textContent).toContain('Left bowler');
    expect(bowlerCells(rows[0])[0].textContent).toContain('(0.1-0-1-0)');
    expect(bowlerCells(rows[0])[1].textContent?.trim()).toBe('—');
    expect(bowlerCells(rows[1])[0].textContent?.trim()).toBe('—');
    expect(bowlerCells(rows[1])[1].textContent).toContain('Right bowler');
    expect(bowlerCells(rows[1])[1].textContent).toContain('(0.1-0-2-0)');
    expect(fixture.nativeElement.querySelectorAll('.matrix-bowler-line')).toHaveSize(2);
  });

  it('should keep repeated bowler names in the same style', async () => {
    const firstOver = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, bowler: 'Left bowler', totalRuns: 1, batterRuns: 1
    });
    const repeatedOver = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 3, bowler: 'Left bowler', totalRuns: 2, batterRuns: 2
    });
    await configure({}, '1000010101', scoresheet(firstOver, repeatedOver));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const bowlerLines = fixture.nativeElement.querySelectorAll('.matrix-bowler-line') as NodeListOf<HTMLElement>;

    expect(bowlerLines).toHaveSize(2);
    expect(Array.from(bowlerLines).every((line) => !line.classList.contains('matrix-bowler-line--repeated'))).toBeTrue();
  });

  it('should show dismissal details for an out batter and in the notes', async () => {
    const dismissal = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'D. Hassan',
      nonStriker: 'Blake', wicketCount: 1, wickets: [{wicketKey: 157781, kind: 'caught', fielders: ['RG Sharma']}]
    });
    await configure({}, '1000010101', scoresheet(dismissal));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const batterCell = fixture.nativeElement.querySelector('.linear-matrix__batter-cell') as HTMLElement;
    const notesCell = fixture.nativeElement.querySelector('.linear-matrix__notes') as HTMLElement;

    expect(batterCell.textContent).toContain('D. Hassan');
    expect(batterCell.textContent).toContain('(0r, 1b)');
    expect(notesCell.textContent).toContain('WICKET 1: D. Hassan — Caught (RG Sharma)');
  });

  it('should show a dismissed batter score with cumulative boundaries and an out marker', async () => {
    const firstDelivery = delivery({
      deliveryKey: 1, sourceBallId: 101, inningsOrder: 1, overNumber: 1, ballInOver: 1,
      batter: 'D. Hassan', nonStriker: 'Blake', batterRuns: 4, totalRuns: 4
    });
    const wideDelivery = delivery({
      deliveryKey: 2, sourceBallId: 102, inningsOrder: 2, overNumber: 1, ballInOver: 2,
      batter: 'D. Hassan', nonStriker: 'Blake', batterRuns: 0, wides: 1, totalRuns: 1
    });
    const secondDelivery = delivery({
      deliveryKey: 3, sourceBallId: 103, inningsOrder: 3, overNumber: 2, ballInOver: 1,
      batter: 'D. Hassan', nonStriker: 'Blake', batterRuns: 6, totalRuns: 6
    });
    const dismissal = delivery({
      deliveryKey: 4, sourceBallId: 104, inningsOrder: 4, overNumber: 2, ballInOver: 2,
      batter: 'D. Hassan', nonStriker: 'Blake', batterRuns: 1, totalRuns: 1, wicketCount: 1,
      wickets: [{wicketKey: 157781, kind: 'caught', fielders: ['RG Sharma']}]
    });
    await configure({}, '1000010101', scoresheet(firstDelivery, wideDelivery, secondDelivery, dismissal));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const dismissedScore = fixture.nativeElement.querySelector('.matrix-dismissed') as HTMLElement;

    expect(dismissedScore.textContent?.trim()).toBe('(11r, 3b, 1x4, 1x6)');
    expect(fixture.nativeElement.textContent).toContain('[OUT]');
  });

  it('should mark only the dismissed batter as out', async () => {
    const dismissal = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'JG Bethell',
      nonStriker: 'BM Duckett', wicketCount: 1, wickets: [{wicketKey: 157781, kind: 'caught', fielders: ['RG Sharma']}]
    });
    await configure({}, '1000010101', scoresheet(dismissal));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const batterCells = fixture.nativeElement.querySelectorAll('.linear-matrix__batter-cell') as NodeListOf<HTMLElement>;
    const dismissedCell = Array.from(batterCells).find((cell) => cell.textContent?.includes('JG Bethell'))!;
    const notDismissedCell = Array.from(batterCells).find((cell) => cell.textContent?.includes('BM Duckett'))!;

    expect(dismissedCell.textContent).toContain('W');
    expect(dismissedCell.textContent).toContain('(0r, 1b)');
    expect(dismissedCell.textContent).toContain('[OUT]');
    expect(notDismissedCell.querySelector('.matrix-dismissed')).toBeNull();
    expect(notDismissedCell.querySelector('.matrix-symbol--wicket')).toBeNull();
  });

  it('should not render a later dismissal against the batter in an earlier row', async () => {
    const earlierDelivery = delivery({
      deliveryKey: 1, sourceBallId: 101, inningsOrder: 1, overNumber: 13, ballInOver: 1,
      batter: 'M Kapp', nonStriker: 'S Verma', batterRuns: 1, totalRuns: 1
    });
    const dismissal = delivery({
      deliveryKey: 2, sourceBallId: 102, inningsOrder: 2, overNumber: 14, ballInOver: 1,
      batter: 'M Kapp', nonStriker: 'D Penna', wicketCount: 1,
      wickets: [{wicketKey: 2, kind: 'caught', fielders: ['Fielder']}]
    });
    await configure({}, '1000010101', scoresheet(earlierDelivery, dismissal));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr') as NodeListOf<HTMLTableRowElement>;

    expect(rows[0].querySelector('.matrix-dismissed')).toBeNull();
    expect(rows[0].textContent).not.toContain('[OUT]');
    expect(rows[1].querySelector('.matrix-dismissed')).not.toBeNull();
  });

  it('should display cumulative wickets in the end-of-over ledger', async () => {
    const firstWicket = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1, batter: 'First Batter',
      nonStriker: 'Second Batter', wicketCount: 1, wickets: [{wicketKey: 1, kind: 'bowled', fielders: []}]
    });
    const secondWicket = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 2, ballInOver: 1, batter: 'Second Batter',
      nonStriker: 'Third Batter', wicketCount: 1, wickets: [{wicketKey: 2, kind: 'caught', fielders: []}]
    });
    await configure({}, '1000010101', scoresheet(firstWicket, secondWicket));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr') as NodeListOf<HTMLTableRowElement>;
    expect((rows[0].querySelector('.linear-matrix__wickets') as HTMLElement).textContent?.trim()).toBe('1');
    expect((rows[1].querySelector('.linear-matrix__wickets') as HTMLElement).textContent?.trim()).toBe('2');
  });

  it('should display cumulative extras by type in the end-of-over ledger', async () => {
    const firstOver = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1,
      byes: 3, wides: 1, totalRuns: 4
    });
    const secondOver = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 2, ballInOver: 1,
      legByes: 2, totalRuns: 2
    });
    const thirdOver = delivery({
      deliveryKey: 3, sourceBallId: 103, overNumber: 3, ballInOver: 1
    });
    await configure({}, '1000010101', scoresheet(firstOver, secondOver, thirdOver));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr') as NodeListOf<HTMLTableRowElement>;
    const extras = Array.from(rows).map((row) =>
      (row.querySelector('.linear-matrix__extras') as HTMLElement).textContent?.trim()
    );

    expect(extras).toEqual(['B:3 W:1 (4)', 'B:3 LB:2 W:1 (6)', 'B:3 LB:2 W:1 (6)']);
  });

  it('should keep batting lanes stable when the strike changes and a batter is replaced', async () => {
    const firstBall = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1, batter: 'BM Duckett',
      nonStriker: 'JG Bethell'
    });
    const secondBall = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'JG Bethell',
      nonStriker: 'BM Duckett'
    });
    const wicketBall = delivery({
      deliveryKey: 3, sourceBallId: 103, overNumber: 2, ballInOver: 1, batter: 'JG Bethell',
      nonStriker: 'BM Duckett', wicketCount: 1,
      wickets: [{wicketKey: 157781, kind: 'caught', fielders: ['RG Sharma']}]
    });
    const incomingBatterBall = delivery({
      deliveryKey: 4, sourceBallId: 104, overNumber: 3, ballInOver: 1, batter: 'BM Duckett',
      nonStriker: 'H Brook'
    });
    await configure({}, '1000010101', scoresheet(firstBall, secondBall, wicketBall, incomingBatterBall));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr') as NodeListOf<HTMLTableRowElement>;
    const batterCells = (row: HTMLTableRowElement): NodeListOf<HTMLElement> =>
      row.querySelectorAll('.linear-matrix__batter-cell');

    expect(batterCells(rows[0])[0].textContent).toContain('BM Duckett');
    expect(batterCells(rows[0])[1].textContent).toContain('JG Bethell');
    expect(batterCells(rows[1])[0].textContent).toContain('BM Duckett');
    expect(batterCells(rows[1])[1].textContent).toContain('JG Bethell');
    expect(batterCells(rows[2])[0].textContent).toContain('BM Duckett');
    expect(batterCells(rows[2])[1].textContent).toContain('H Brook');
    expect(rows[0].querySelectorAll('.matrix-player-header')).toHaveSize(2);
    expect(rows[1].querySelectorAll('.matrix-player-header')).toHaveSize(2);
    expect(rows[2].querySelectorAll('.matrix-player-header')).toHaveSize(2);
    expect(rows[0].querySelectorAll('.matrix-player-header--repeated')).toHaveSize(0);
    expect(rows[1].querySelectorAll('.matrix-player-header--repeated')).toHaveSize(0);
    expect(rows[2].querySelectorAll('.matrix-player-header--repeated')).toHaveSize(0);
  });
});

async function configure(
  query: Record<string, string>,
  publicMatchId = '1000010101',
  result: MatchScoresheetResponse = scoresheet(),
  paramMap: Observable<ReturnType<typeof convertToParamMap>> = of(convertToParamMap({publicMatchId})),
  getScoresheet = () => of({result})
): Promise<void> {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [SelectedMatchPlaceholderComponent],
    providers: [
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {paramMap: convertToParamMap({publicMatchId}), queryParamMap: convertToParamMap(query)},
          paramMap
        }
      },
      {
        provide: MatchService,
        useValue: {getScoresheet}
      }
    ]
  }).compileComponents();
}

function scoresheet(...deliveries: ScoresheetDelivery[]): MatchScoresheetResponse {
  return scoresheetWithInnings(innings(1, 'England', 'India', ...deliveries));
}

function scoresheetWithInnings(...inningsList: ScoresheetInnings[]): MatchScoresheetResponse {
  return {
    context: {publicMatchId: 1_000_010_101, fileName: 'match.json', team1: 'England', team2: 'India'},
    completeness: inningsList.some((current) => current.deliveries.length > 0) ? 'COMPLETE' : 'EMPTY',
    missingData: inningsList.some((current) => current.deliveries.length > 0) ? [] : ['deliveries'],
    innings: inningsList
  };
}

function innings(
  inningsNumber: number,
  battingTeam: string,
  bowlingTeam: string,
  ...deliveries: ScoresheetDelivery[]
): ScoresheetInnings {
  return {inningsNumber, battingTeam, bowlingTeam, declared: false, deliveries};
}

function delivery(overrides: Partial<ScoresheetDelivery>): ScoresheetDelivery {
  return {
    deliveryKey: 1,
    sourceBallId: 1,
    inningsOrder: 1,
    overNumber: 1,
    ballNumber: 1,
    ballInOver: 1,
    batter: 'Batter',
    nonStriker: 'Non-striker',
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
