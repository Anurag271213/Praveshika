import React, { createContext, useContext, useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('auth.token'));
  const [user, setUser] = useState(() => {
    try {
      const savedUser = localStorage.getItem('auth.user');
      return savedUser ? JSON.parse(savedUser) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Validate session on boot if token exists
    async function verifySession() {
      if (token) {
        try {
          const res = await axiosClient.get('/api/auth/me');
          setUser(res.data);
          localStorage.setItem('auth.user', JSON.stringify(res.data));
        } catch {
          // If token expired or invalid, clear credentials
          localStorage.removeItem('auth.token');
          localStorage.removeItem('auth.user');
          setToken(null);
          setUser(null);
        }
      }
      setLoading(false);
    }

    verifySession();

    // Listen for 401 logout events emitted by axios interceptor
    const handleLogoutEvent = () => {
      setToken(null);
      setUser(null);
    };

    window.addEventListener('auth:logout', handleLogoutEvent);
    return () => window.removeEventListener('auth:logout', handleLogoutEvent);
  }, [token]);

  const login = async (usernameOrEmail, password) => {
    const res = await axiosClient.post('/api/auth/login', {
      usernameOrEmail,
      password,
    });

    const data = res.data;
    if (data && data.token) {
      localStorage.setItem('auth.token', data.token);
      localStorage.setItem('auth.user', JSON.stringify(data.user));
      setToken(data.token);
      setUser(data.user);
    }
    return data;
  };

  const logout = async () => {
    try {
      await axiosClient.post('/api/auth/logout');
    } catch {
      // Ignore network errors during logout
    } finally {
      localStorage.removeItem('auth.token');
      localStorage.removeItem('auth.user');
      setToken(null);
      setUser(null);
    }
  };

  const value = {
    user,
    token,
    isAuthenticated: Boolean(token && user),
    loading,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
