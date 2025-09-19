import { z } from 'zod'
import { zodDurationString } from './duration'

export type FrequencyEntry = {
    values: string[];
};

export type FrequenciesMap = Record<string, FrequencyEntry>;

export type FrequencyEditorComponentItem = {
    value: string;
};

export const frequencyEditorComponentItemInit = () =>
    ({
        value: '',
    }) as FrequencyEditorComponentItem

const FrequencyEditorComponentItemSchema = z.object({
    value: zodDurationString,
})

export type FrequencyEditorRow = {
    frequency: string;
    values: FrequencyEditorComponentItem[];
};

export const frequencyEditorRowInit = () =>
    ({
        frequency: '',
        values: [frequencyEditorComponentItemInit()],
    }) as FrequencyEditorRow

const FrequencyEditorRowSchema = z.object({
    frequency: z.string().nonempty('frequency name cannot be empty'),
    values: z.array(FrequencyEditorComponentItemSchema),
})

export type FrequencyEditorDataContainer = {
    frequencies: FrequencyEditorRow[];
};

export const FrequencyEditorDataContainerSchema = z.object({
    frequencies: z.array(FrequencyEditorRowSchema).superRefine((data, ctx) => {
        const seen: string[] = []
        for (const [i, d] of data.entries()) {
            if (seen.includes(d.frequency)) {
                ctx.addIssue({
                    code: 'custom',
                    path: [i, 'frequency'],
                    message: 'Frequency respecified',
                })
            } else seen.push(d.frequency)
        }
    }),
})
