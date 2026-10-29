import { SimpleChange } from '@angular/core';
import * as L from 'leaflet';

import { DepotDTO, OrderDTO, SolutionDTO } from '@core/models';
import { MapService } from '@shared/services/map.service';

import { MapComponent } from './map.component';

/**
 * MO-T5 of MAP-ORDERS-001: clearing a solution redraws depots and the
 * table's orders. MapService is a spy, so no Leaflet map is needed.
 */
describe('MapComponent solution clear', () => {
  let mapService: jasmine.SpyObj<MapService>;
  let component: MapComponent;

  const depots = [{ id: 1 }] as unknown as DepotDTO[];
  const orders = [{ id: 7 }] as unknown as OrderDTO[];
  const solution = { id: 3, routes: [] } as unknown as SolutionDTO;

  beforeEach(() => {
    jasmine.clock().install();
    mapService = jasmine.createSpyObj<MapService>('MapService', [
      'initializeMap', 'loadDepots', 'loadOrders', 'displaySolution',
      'clearSolution', 'clearAll', 'highlightOrder', 'invalidateSize'
    ]);
    mapService.displaySolution.and.resolveTo();
    component = new MapComponent(mapService);
    component.depots = depots;
    component.orders = orders;
    component.onMapReady({} as L.Map);
    mapService.loadDepots.calls.reset();
    mapService.loadOrders.calls.reset();
  });

  afterEach(() => jasmine.clock().uninstall());

  it('MO-T5: solution set then cleared redraws depots and orders without clearAll', () => {
    component.solution = solution;
    component.ngOnChanges({ solution: new SimpleChange(null, solution, false) });
    expect(mapService.displaySolution).toHaveBeenCalledWith(solution);

    component.solution = null;
    component.ngOnChanges({ solution: new SimpleChange(solution, null, false) });

    expect(mapService.clearSolution).toHaveBeenCalledTimes(1);
    expect(mapService.loadDepots).toHaveBeenCalledWith(depots);
    expect(mapService.loadOrders).toHaveBeenCalledTimes(1);
    expect(mapService.loadOrders.calls.mostRecent().args[0]).toBe(orders);
    expect(mapService.clearAll).not.toHaveBeenCalled();
  });

  it('keeps order markers hidden while a solution is shown, then draws the latest page', () => {
    component.solution = solution;
    component.ngOnChanges({ solution: new SimpleChange(null, solution, false) });

    const nextPage = [{ id: 8 }] as unknown as OrderDTO[];
    component.orders = nextPage;
    component.ngOnChanges({ orders: new SimpleChange(orders, nextPage, false) });
    expect(mapService.loadOrders).not.toHaveBeenCalled();

    component.solution = null;
    component.ngOnChanges({ solution: new SimpleChange(solution, null, false) });
    expect(mapService.loadOrders.calls.mostRecent().args[0]).toBe(nextPage);
  });

  it('draws every new order page while no solution is shown', () => {
    const nextPage = [{ id: 9 }] as unknown as OrderDTO[];
    component.orders = nextPage;
    component.ngOnChanges({ orders: new SimpleChange(orders, nextPage, false) });

    expect(mapService.loadOrders).toHaveBeenCalledTimes(1);
    expect(mapService.loadOrders.calls.mostRecent().args[0]).toBe(nextPage);
  });
});
