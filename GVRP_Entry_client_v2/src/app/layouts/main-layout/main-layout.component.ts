import { Component } from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {NavbarComponent} from '@features/main/components/navbar/navbar.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    NavbarComponent
  ],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss'
})
export class MainLayoutComponent {
  onMenuClick() {
    // Handle sidebar toggle for mobile
    // TODO
  }

  onTabChange(tab: string) {
    console.log('Active tab:', tab);
    // Handle tab navigation if needed
  }
}
