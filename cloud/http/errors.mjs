export class ServiceError extends Error {
  constructor(code, message, status = 400) {
    super(message);
    this.code = code;
    this.status = status;
  }
}
export const fail = (code, message, status = 400) => {
  throw new ServiceError(code, message, status);
};
export function requireValue(condition, code, message, status = 400) {
  if (!condition) fail(code, message, status);
}
