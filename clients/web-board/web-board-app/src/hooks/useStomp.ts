import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_BASE } from '../config';

type MessageCallback = (body: string) => void;

export function useStomp(
  topics: string[],
  onMessage: MessageCallback,
) {
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      reconnectDelay: 5000,
      onConnect: () => {
        topics.forEach(topic => {
          client.subscribe(topic, frame => {
            if (frame.body) onMessage(frame.body);
          });
        });
      },
    });
    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
}
