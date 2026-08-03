import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      this.router.navigateByUrl(this.auth.homeRoute());
    }
  }

  protected staffCode = '';
  protected pin = '';
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);

  protected async submit(): Promise<void> {
    if (this.submitting()) {
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    try {
      await this.auth.login(this.staffCode.trim(), this.pin.trim());
      await this.router.navigateByUrl(this.auth.homeRoute());
    } catch (e) {
      this.error.set(messageFor(e));
    } finally {
      this.submitting.set(false);
    }
  }
}

function messageFor(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) {
      return 'Cannot reach the toll service. Is it running on port 8080?';
    }
    return e.error?.message ?? 'Sign in failed';
  }
  return 'Sign in failed';
}
