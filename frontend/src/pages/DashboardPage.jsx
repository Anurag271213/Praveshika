import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import axiosClient from '../api/axiosClient';

export default function DashboardPage() {
  const { user, token, logout } = useAuth();
  const [healthStatus, setHealthStatus] = useState('Checking...');
  const [profileData, setProfileData] = useState(user);
  const [loadingProfile, setLoadingProfile] = useState(false);

  useEffect(() => {
    // Verify protected GET /api/auth/me endpoint
    async function loadFreshProfile() {
      setLoadingProfile(true);
      try {
        const res = await axiosClient.get('/api/auth/me');
        setProfileData(res.data);
      } catch (err) {
        console.error('Failed to fetch user profile:', err);
      } finally {
        setLoadingProfile(false);
      }
    }

    // Check backend health
    async function checkHealth() {
      try {
        const res = await axiosClient.get('/api/health');
        setHealthStatus(res.data.status || 'UP');
      } catch {
        setHealthStatus('UNREACHABLE');
      }
    }

    loadFreshProfile();
    checkHealth();
  }, []);

  const displayUser = profileData || user || {};

  return (
    <div className="dashboard-container">
      <div className="welcome-banner">
        <div className="welcome-text">
          <h1>Welcome, {displayUser.username || 'User'}!</h1>
          <p>You have successfully authenticated via Spring Security & JWT.</p>
        </div>
        <div className="status-badge active" id="session-status-badge">
          <span className="status-dot"></span>
          <span>Session Active</span>
        </div>
      </div>

      <div className="grid-cards">
        <div className="info-card" id="user-profile-card">
          <div className="card-label">User Profile</div>
          <div className="card-value" style={{ color: '#60a5fa' }}>
            {displayUser.username || '—'}
          </div>
          <div className="card-subtext">
            Email: <span style={{ color: 'var(--text-primary)' }}>{displayUser.email || '—'}</span>
          </div>
          <div className="card-subtext">
            User ID: <span style={{ color: 'var(--text-primary)' }}>#{displayUser.id || '—'}</span>
          </div>
        </div>

        <div className="info-card" id="token-card">
          <div className="card-label">Authentication Token</div>
          <div className="card-value" style={{ fontSize: '0.95rem' }}>
            <span className="mono-tag" title={token || ''}>
              {token ? `${token.substring(0, 24)}...` : 'None'}
            </span>
          </div>
          <div className="card-subtext">
            Type: <span style={{ color: 'var(--text-primary)' }}>Bearer (HMAC-SHA256)</span>
          </div>
          <div className="card-subtext">
            Scope: <span style={{ color: 'var(--text-primary)' }}>Stateless (No Session Cookie)</span>
          </div>
        </div>

        <div className="info-card" id="backend-health-card">
          <div className="card-label">Backend Service</div>
          <div className="card-value">
            <span style={{ color: healthStatus === 'UP' ? '#34d399' : '#f87171' }}>
              {healthStatus}
            </span>
          </div>
          <div className="card-subtext">
            Endpoint: <span style={{ color: 'var(--text-primary)' }}>GET /api/health</span>
          </div>
          <div className="card-subtext">
            Profile API: <span style={{ color: 'var(--text-primary)' }}>GET /api/auth/me</span>
          </div>
        </div>
      </div>

      <div className="info-card" style={{ marginTop: '1rem' }}>
        <h3 style={{ marginBottom: '0.75rem', fontSize: '1.1rem' }}>Security & Session Controls</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '1.25rem' }}>
          This session is guarded by a client-side JWT stored in localStorage and validated against Spring Security on every protected request.
        </p>
        <button
          id="dashboard-logout-btn"
          type="button"
          className="btn btn-outline"
          onClick={logout}
          style={{ width: 'auto', padding: '0.6rem 1.25rem' }}
        >
          Sign Out of Session
        </button>
      </div>
    </div>
  );
}
