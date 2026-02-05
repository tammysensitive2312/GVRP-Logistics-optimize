import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule
} from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';

import { AuthService } from '@core/services/auth.service';
import { ToastService } from '@shared/services/toast.service';
import { LoadingService } from '@shared/services/loading.service';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;
  loading = false;
  showPassword = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private toast: ToastService,
    private loadingService: LoadingService
  ) {}

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/main']);
      return;
    }

    this.initForm();
  }

  private initForm(): void {
    this.loginForm = this.fb.group({
      branch_name: ['', [Validators.required]],
      username: ['', [Validators.required]],
      password: ['', [Validators.required]],
      rememberMe: [false]
    });
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.markFormGroupTouched(this.loginForm);
      return;
    }

    this.loadingService.show("Account verification in progress...");
    const { rememberMe, ...credentials } = this.loginForm.value;

    this.authService.login(credentials, rememberMe)
      .pipe(
        finalize(() => {
          this.loadingService.hide();
          this.loading = false;
        })
      )
      .subscribe({
        next: () => {
          this.toast.success('Login successful!');
          const redirectUrl = this.authService.getRedirectUrl();
          this.router.navigate([redirectUrl]);
        },
        error: (error) => {
          this.toast.error(error.message || 'Login failed!');
        }
      });
  }

  private markFormGroupTouched(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(key => {
      const control = formGroup.get(key);
      control?.markAsTouched();
    });
  }

}
