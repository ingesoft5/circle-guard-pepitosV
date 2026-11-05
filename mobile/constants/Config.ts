/**
 * Global Configuration for CircleGuard Mobile.
 *
 * EXPO_PUBLIC_API_PREFIX se inyecta en tiempo de build (Expo lo reemplaza
 * por su valor dentro del bundle):
 *  - Sin definir (`expo start` en local): cada servicio en localhost:<puerto>.
 *  - "/svc" (imagen web, ver Dockerfile): rutas relativas del mismo origen,
 *    p. ej. /svc/auth/api/v1/..., que el nginx del contenedor enruta al
 *    servicio del ambiente donde corre (dev, stage o prod).
 *  - Una URL absoluta (p. ej. "https://api.dev.circleguard.edu/svc") si el
 *    API se expone en otro dominio.
 */
const API_PREFIX = process.env.EXPO_PUBLIC_API_PREFIX;

const serviceUrl = (service: string, localPort: number): string =>
  API_PREFIX
    ? `${API_PREFIX.replace(/\/+$/, '')}/${service}`
    : `http://localhost:${localPort}`;

export const AUTH_BASE_URL = serviceUrl('auth', 8180); // Auth Service
export const DASHBOARD_BASE_URL = serviceUrl('dashboard', 8084); // Dashboard Service
export const FILE_BASE_URL = serviceUrl('file', 8085); // File Service
export const FORM_BASE_URL = serviceUrl('form', 8086); // Form Service
export const GATEWAY_BASE_URL = serviceUrl('gateway', 8087); // Gateway Service
export const IDENTITY_BASE_URL = serviceUrl('identity', 8083); // Identity Service
export const NOTIFICATION_BASE_URL = serviceUrl('notification', 8082); // Notification Service
export const PROMOTION_BASE_URL = serviceUrl('promotion', 8088); // Promotion Service

/** Usado por hooks/useGateValidation.ts (validación de QR en el Gateway). */
export const API_BASE_URL = GATEWAY_BASE_URL;
