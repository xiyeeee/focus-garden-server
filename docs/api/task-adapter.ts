import type { TaskCreateDTO, TaskPlantType, TaskUpdateDTO, TaskVO } from './task-types';

/** 与现有 focus-garden-client/src/types/index.ts 的任务模型一致。 */
export interface ClientTask {
  id: string;
  title: string;
  notes: string;
  plannedMinutes: number;
  plantType: TaskPlantType;
  status: 'pending' | 'completed';
  createdAt: number;
  completedAt: number | null;
}
export type ClientTaskInput = Pick<ClientTask, 'title' | 'notes' | 'plannedMinutes' | 'plantType'>;

export const toClientTask = (task: TaskVO): ClientTask => ({
  id: task.id,
  title: task.title,
  notes: task.description,
  plannedMinutes: task.plannedFocusMinutes,
  plantType: task.plantType,
  status: task.status === 'COMPLETED' ? 'completed' : 'pending',
  createdAt: Date.parse(task.createdAt),
  completedAt: task.completedAt === null ? null : Date.parse(task.completedAt),
});

export const toTaskCreateDTO = (input: ClientTaskInput): TaskCreateDTO => ({
  title: input.title,
  description: input.notes,
  plannedFocusMinutes: input.plannedMinutes,
  plantType: input.plantType,
  status: 'TODO',
});

/** 保留原始 VO 状态，避免把 IN_PROGRESS 在编辑时意外降为 TODO。 */
export const toTaskUpdateDTO = (input: ClientTaskInput, original: TaskVO): TaskUpdateDTO => ({
  ...toTaskCreateDTO(input),
  status: original.status,
});
