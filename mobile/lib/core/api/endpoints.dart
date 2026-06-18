const String kBaseUrl = String.fromEnvironment(
  'BASE_URL',
  defaultValue: 'http://10.0.2.2:8080',
);

const String kWsUrl = String.fromEnvironment(
  'WS_URL',
  defaultValue: 'ws://10.0.2.2:8080/ws/availability',
);
