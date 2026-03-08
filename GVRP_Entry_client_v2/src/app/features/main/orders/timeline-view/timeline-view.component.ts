import {Component, Input} from '@angular/core';
import {SolutionDTO} from '@core/models';

@Component({
  selector: 'app-timeline-view',
  standalone: true,
  imports: [],
  templateUrl: './timeline-view.component.html',
  styleUrl: './timeline-view.component.scss'
})
export class TimelineViewComponent {
  @Input() solution!: SolutionDTO;

}
