import { useStomp as useSharedStomp } from '../../../../shared/hooks/useStomp';
import { API_BASE } from '../config';

type MessageCallback = (body: string, topic: string) => void;

/** STOMP hook с базовым URL диспетчера. */
export function useStomp(topics: string[], onMessage: MessageCallback) {
  useSharedStomp(API_BASE, topics, onMessage);
}
