import http from 'k6/http';
import { sleep } from 'k6';
import { config, jsonHeaders } from '../lib/config.js';
import { bootstrap, createOrder, addItemToOrder } from '../lib/setup.js';

const vusPerScenario = Number(__ENV.VUS_PER_SCENARIO || 10);
const duration = __ENV.DURATION || '30s';

export function setup() {
  return bootstrap();
}

export const options = {
  scenarios: {
    auth_login: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'authLogin',
      tags: { module: 'auth' },
    },
    auth_users: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'authUsers',
      tags: { module: 'auth' },
    },
    products_list: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'productsList',
      tags: { module: 'product' },
    },
    products_by_id: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'productsById',
      tags: { module: 'product' },
    },
    customers_list: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'customersList',
      tags: { module: 'customer' },
    },
    customers_by_id: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'customersById',
      tags: { module: 'customer' },
    },
    orders_list: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'ordersList',
      tags: { module: 'order' },
    },
    orders_create: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'ordersCreate',
      tags: { module: 'order' },
    },
    orders_add_item: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'ordersAddItem',
      tags: { module: 'order' },
    },
    orders_pay: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'ordersPay',
      tags: { module: 'order' },
    },
    payments_process: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'paymentsProcess',
      tags: { module: 'payment' },
    },
    recommendations_read: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'recommendationsRead',
      tags: { module: 'recommendation' },
    },
    recommendations_views: {
      executor: 'constant-vus',
      vus: vusPerScenario,
      duration,
      exec: 'recommendationsViews',
      tags: { module: 'recommendation' },
    },
    checkout_flow: {
      executor: 'constant-vus',
      vus: Math.max(5, Math.floor(vusPerScenario / 2)),
      duration,
      exec: 'checkoutFlow',
      tags: { module: 'checkout' },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.15'],
  },
};

export function authLogin() {
  http.post(
    `${config.baseUrl}/api/auth/login`,
    JSON.stringify({ email: config.email, password: config.password }),
    { ...jsonHeaders, tags: { endpoint: 'login', store: 'postgres' } }
  );
  sleep(config.pause);
}

export function authUsers() {
  http.get(`${config.baseUrl}/api/auth/users`, {
    tags: { endpoint: 'users', store: 'postgres' },
  });
  sleep(config.pause);
}

export function productsList() {
  http.get(`${config.baseUrl}/api/products`, {
    tags: { endpoint: 'products-list', store: 'postgres' },
  });
  sleep(config.pause);
}

export function productsById(data) {
  http.get(`${config.baseUrl}/api/products/${data.productId}`, {
    tags: { endpoint: 'products-by-id', store: 'postgres' },
  });
  sleep(config.pause);
}

export function customersList() {
  http.get(`${config.baseUrl}/api/customers`, {
    tags: { endpoint: 'customers-list', store: 'postgres' },
  });
  sleep(config.pause);
}

export function customersById(data) {
  http.get(`${config.baseUrl}/api/customers/${data.customerId}`, {
    tags: { endpoint: 'customers-by-id', store: 'postgres' },
  });
  sleep(config.pause);
}

export function ordersList(data) {
  http.get(`${config.baseUrl}/api/orders/customer/${data.customerId}`, {
    tags: { endpoint: 'orders-list', store: 'postgres' },
  });
  sleep(config.pause);
}

export function ordersCreate(data) {
  http.post(
    `${config.baseUrl}/api/orders`,
    JSON.stringify({ customerId: data.customerId }),
    { ...jsonHeaders, tags: { endpoint: 'orders-create', store: 'postgres' } }
  );
  sleep(config.pause);
}

export function ordersAddItem(data) {
  const order = createOrder(data.customerId);
  http.post(
    `${config.baseUrl}/api/orders/${order.id}/items`,
    JSON.stringify({
      productId: data.productId,
      productName: data.productName,
      quantity: 1,
      unitPrice: data.productPrice,
    }),
    { ...jsonHeaders, tags: { endpoint: 'orders-add-item', store: 'postgres' } }
  );
  sleep(config.pause);
}

export function ordersPay(data) {
  const order = createOrder(data.customerId);
  addItemToOrder(order.id, data);
  http.post(`${config.baseUrl}/api/orders/${order.id}/pay`, null, {
    tags: { endpoint: 'orders-pay', store: 'postgres' },
  });
  sleep(config.pause);
}

export function paymentsProcess(data) {
  const order = createOrder(data.customerId);
  addItemToOrder(order.id, data);
  http.post(
    `${config.baseUrl}/api/payments`,
    JSON.stringify({ orderId: order.id, amount: data.productPrice, method: 'PIX' }),
    { ...jsonHeaders, tags: { endpoint: 'payments-process', store: 'postgres' } }
  );
  sleep(config.pause);
}

export function recommendationsRead(data) {
  http.get(`${config.baseUrl}/api/recommendations/customers/${data.customerId}`, {
    tags: { endpoint: 'recommendations-read', store: 'redis' },
  });
  sleep(config.pause);
}

export function recommendationsViews(data) {
  http.post(
    `${config.baseUrl}/api/recommendations/customers/${data.customerId}/views`,
    JSON.stringify({ productId: data.productId }),
    { ...jsonHeaders, tags: { endpoint: 'recommendations-views', store: 'redis' } }
  );
  sleep(config.pause);
}

export function checkoutFlow(data) {
  const orderRes = http.post(
    `${config.baseUrl}/api/orders`,
    JSON.stringify({ customerId: data.customerId }),
    { ...jsonHeaders, tags: { endpoint: 'checkout', store: 'postgres' } }
  );

  if (orderRes.status !== 201) {
    sleep(config.pause);
    return;
  }

  const orderId = orderRes.json().id;

  http.post(
    `${config.baseUrl}/api/orders/${orderId}/items`,
    JSON.stringify({
      productId: data.productId,
      productName: data.productName,
      quantity: 1,
      unitPrice: data.productPrice,
    }),
    { ...jsonHeaders, tags: { endpoint: 'checkout', store: 'postgres' } }
  );

  http.post(
    `${config.baseUrl}/api/payments`,
    JSON.stringify({ orderId, amount: data.productPrice, method: 'PIX' }),
    { ...jsonHeaders, tags: { endpoint: 'checkout', store: 'postgres' } }
  );

  sleep(config.pause);
}
