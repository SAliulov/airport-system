import { useEffect, useRef } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

type MessageCallback = (body: string, topic: string) => void;

/**
 * Подписка на STOMP-топики через SockJS. Callback стабилен через ref.
 */
export function useStomp(apiBase: string, topics: string[], onMessage: MessageCallback) {
  const cbRef = useRef(onMessage);
  cbRef.current = onMessage;

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${apiBase}/ws`) as WebSocket,
      reconnectDelay: 5000,
      onConnect: () => {
        topics.forEach(topic => {
          client.subscribe(topic, (frame: IMessage) => {
            if (frame.body) cbRef.current(frame.body, topic);
          });
        });
      },
    });
    client.activate();
    return () => {
      client.deactivate();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- topics fixed at mount
  }, [apiBase]);
}
