import {Component, Input} from '@angular/core';
import {SolutionDTO} from '@core/models';

@Component({
  selector: 'app-route-view',
  standalone: true,
  imports: [],
  templateUrl: './route-view.component.html',
  styleUrl: './route-view.component.scss'
})
export class RouteViewComponent {
  @Input() solution!: SolutionDTO;
}
