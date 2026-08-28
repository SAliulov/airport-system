import { useEffect, useRef, useState } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

type MessageCallback = (body: string, topic: string) => void;

function resolveWsUrl(apiBase: string): string {
  if (apiBase.startsWith('http://') || apiBase.startsWith('https://')) {
    const url = new URL(apiBase);
    return `${url.origin}/ws`;
  }
  return `${window.location.protocol}//${window.location.host}/ws`;
}

/**
 * Подписка на STOMP-топики через SockJS. Callback стабилен через ref.
 * SockJS сам договаривается о транспорте (в т.ч. о WebSocket) через HTTP-хендшейк,
 * поэтому ему нужен http(s)://-адрес, а не ws(s)://.
 */
export function useStomp(apiBase: string, topics: string[], onMessage: MessageCallback) {
  const cbRef = useRef(onMessage);
  cbRef.current = onMessage;
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const wsUrl = resolveWsUrl(apiBase);
    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl, undefined, { transports: ['websocket', 'xhr-streaming', 'xhr-polling'] }) as WebSocket,
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        topics.forEach(topic => {
          client.subscribe(topic, (frame: IMessage) => {
            if (frame.body) cbRef.current(frame.body, topic);
          });
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();
    return () => {
      client.deactivate();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- topics fixed at mount
  }, [apiBase]);

  return { connected };
}
