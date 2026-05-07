import { z } from 'zod'
import type { AuditSource } from './types'

const uuidRegex = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$/

export const auditSourceSchema = z.enum(['identity', 'workspace', 'content'])

/** Form/state often uses `null`; URL parsing may yield `undefined` — normalize both to output null. */
function nullishToUndefined<T>(v: T): T | undefined {
  return v === null || v === undefined ? undefined : v
}

const optionalUuid = z.preprocess(
  (v) => nullishToUndefined(v === '' ? null : v),
  z.string().regex(uuidRegex).optional(),
).transform((v) => v ?? null)

const isoOrEmpty = z.preprocess(nullishToUndefined, z.string().optional()).transform((v) =>
  !v || v.trim() === '' ? null : v.trim(),
)

const optionalTrimmedString = z.preprocess(nullishToUndefined, z.string().optional()).transform((v) =>
  !v?.trim() ? null : v.trim(),
)

export const auditQueryFiltersSchema = z
  .object({
    source: auditSourceSchema,
    eventType: optionalTrimmedString,
    actorUserId: optionalUuid,
    workspaceId: optionalUuid,
    aggregateType: optionalTrimmedString,
    aggregateId: optionalUuid,
    requestId: optionalTrimmedString,
    createdFrom: isoOrEmpty,
    createdTo: isoOrEmpty,
    page: z.coerce.number().int().min(0).default(0),
    size: z.coerce.number().int().min(25).max(200).default(50),
    sort: z
      .string()
      .default('createdAt,desc')
      .refine(
        (s) =>
          /^createdAt,(asc|desc)$/.test(s) ||
          /^eventType,(asc|desc)$/.test(s) ||
          /^aggregateType,(asc|desc)$/.test(s),
        { message: 'Invalid sort pattern' },
      ),
  })
  .superRefine((data, ctx) => {
    if (data.createdFrom && data.createdTo) {
      const from = Date.parse(data.createdFrom)
      const to = Date.parse(data.createdTo)
      if (!Number.isNaN(from) && !Number.isNaN(to) && from > to) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'createdFrom must be before or equal to createdTo',
          path: ['createdTo'],
        })
      }
    }
  })

export type AuditFiltersFormValues = z.infer<typeof auditQueryFiltersSchema>

export function defaultAuditFilters(source: AuditSource = 'workspace'): AuditFiltersFormValues {
  return {
    source,
    eventType: null,
    actorUserId: null,
    workspaceId: null,
    aggregateType: null,
    aggregateId: null,
    requestId: null,
    createdFrom: null,
    createdTo: null,
    page: 0,
    size: 50,
    sort: 'createdAt,desc',
  }
}
