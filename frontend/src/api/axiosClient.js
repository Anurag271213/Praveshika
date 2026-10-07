import axios from 'axios';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '';

const axiosClient = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach Authorization Bearer token to requests
axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('auth.token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Intercept 401 responses to auto-clear session
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Do not redirect if the 401 came from the login endpoint itself
      const requestUrl = error.config ? error.config.url : '';
      if (!requestUrl.includes('/api/auth/login')) {
        localStorage.removeItem('auth.token');
        localStorage.removeItem('auth.user');
        window.dispatchEvent(new Event('auth:logout'));
      }
    }
    return Promise.reject(error);
  }
);

export default axiosClient;
