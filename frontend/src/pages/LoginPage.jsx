import React, { useState } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    usernameOrEmail: '',
    password: '',
  });

  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // If already logged in, redirect to dashboard
  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  const validateField = (name, value) => {
    let error = '';
    const trimmed = value.trim();

    if (name === 'usernameOrEmail') {
      if (!trimmed) {
        error = 'Username or email is required';
      } else if (trimmed.length > 100) {
        error = 'Username or email must not exceed 100 characters';
      } else if (trimmed.includes('@')) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(trimmed)) {
          error = 'Please enter a valid email address';
        }
      }
    }

    if (name === 'password') {
      if (!value) {
        error = 'Password is required';
      } else if (value.length < 6) {
        error = 'Password must be at least 6 characters';
      } else if (value.length > 128) {
        error = 'Password must not exceed 128 characters';
      }
    }

    return error;
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));

    // Clear field-specific error as user types
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: '' }));
    }
    if (serverError) {
      setServerError('');
    }
  };

  const handleBlur = (e) => {
    const { name, value } = e.target;
    const error = validateField(name, value);
    if (error) {
      setErrors((prev) => ({ ...prev, [name]: error }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError('');

    // Run client validation
    const usernameOrEmailErr = validateField('usernameOrEmail', formData.usernameOrEmail);
    const passwordErr = validateField('password', formData.password);

    if (usernameOrEmailErr || passwordErr) {
      setErrors({
        usernameOrEmail: usernameOrEmailErr,
        password: passwordErr,
      });
      return;
    }

    setIsSubmitting(true);

    try {
      await login(formData.usernameOrEmail.trim(), formData.password);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      if (err.response) {
        const status = err.response.status;
        const data = err.response.data;

        if (status === 401) {
          setServerError(data?.message || 'Invalid username/email or password');
        } else if (status === 400) {
          if (data?.fieldErrors && Array.isArray(data.fieldErrors)) {
            const mapped = {};
            data.fieldErrors.forEach((fe) => {
              mapped[fe.field] = fe.message;
            });
            setErrors(mapped);
          }
          setServerError(data?.message || 'Validation failed. Please verify your inputs.');
        } else {
          setServerError(data?.message || 'Authentication service error. Please try again.');
        }
      } else {
        setServerError('Server is unreachable. Please verify network connection.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="main-content">
      <div className="auth-card">
        <div className="card-header">
          <h1 className="card-title">Welcome Back</h1>
          <p className="card-subtitle">Sign in to your account using username or email</p>
        </div>

        {serverError && (
          <div className="alert alert-error" id="server-error-alert" role="alert">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, marginTop: '2px' }}>
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span>{serverError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label className="form-label" htmlFor="usernameOrEmail">
              Username or Email
            </label>
            <div className="input-wrapper">
              <input
                id="usernameOrEmail"
                name="usernameOrEmail"
                type="text"
                autoComplete="username"
                className={`form-input ${errors.usernameOrEmail ? 'has-error' : ''}`}
                placeholder="e.g. admin or admin@example.com"
                value={formData.usernameOrEmail}
                onChange={handleChange}
                onBlur={handleBlur}
                disabled={isSubmitting}
              />
            </div>
            {errors.usernameOrEmail && (
              <p className="error-hint" id="usernameOrEmail-error">
                {errors.usernameOrEmail}
              </p>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="password">
              Password
            </label>
            <div className="input-wrapper">
              <input
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                className={`form-input ${errors.password ? 'has-error' : ''}`}
                placeholder="Enter your password"
                value={formData.password}
                onChange={handleChange}
                onBlur={handleBlur}
                disabled={isSubmitting}
                style={{ paddingRight: '4.5rem' }}
              />
              <button
                type="button"
                id="toggle-password-btn"
                className="password-toggle-btn"
                onClick={() => setShowPassword((prev) => !prev)}
                tabIndex={-1}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
            {errors.password && (
              <p className="error-hint" id="password-error">
                {errors.password}
              </p>
            )}
          </div>

          <button
            id="login-submit-btn"
            type="submit"
            className="btn btn-primary"
            disabled={isSubmitting}
            style={{ marginTop: '0.5rem' }}
          >
            {isSubmitting ? (
              <>
                <div className="spinner" />
                <span>Authenticating...</span>
              </>
            ) : (
              <span>Sign In</span>
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
