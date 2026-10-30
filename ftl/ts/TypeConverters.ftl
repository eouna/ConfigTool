import type { JsonRecord } from "./Types.js";
import { isJsonRecord } from "./Types.js";

/**
 * JSON原始值 -> TypeScript字段值
 *
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 */

export function asBoolean(value: unknown): boolean {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "number") {
    return value !== 0;
  }
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (["true", "1", "yes", "on"].includes(normalized)) {
      return true;
    }
    if (["false", "0", "no", "off", ""].includes(normalized)) {
      return false;
    }
  }
  throw new Error("无法转换为boolean: " + String(value));
}

export function asNumber(value: unknown): number {
  if (typeof value === "number") {
    if (Number.isFinite(value)) {
      return value;
    }
  } else if (typeof value === "boolean") {
    return value ? 1 : 0;
  } else if (value === null || value === undefined || value === "") {
    return 0;
  } else {
    const numberValue = Number(value);
    if (Number.isFinite(numberValue)) {
      return numberValue;
    }
  }
  throw new Error("无法转换为number: " + String(value));
}

export function asString(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return JSON.stringify(value);
}

function parseCollection(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (value === null || value === undefined || value === "") {
    return [];
  }
  if (typeof value === "string") {
    const text = value.trim();
    if (text.startsWith("[")) {
      try {
        const parsed: unknown = JSON.parse(text);
        return Array.isArray(parsed) ? parsed : [parsed];
      } catch {
        // 保持原始标量, 由字段转换器继续处理
      }
    }
    return text === "" ? [] : [text];
  }
  return [value];
}

export function toList<T>(
  value: unknown,
  converter: (item: unknown) => T,
): T[] {
  return parseCollection(value).map(converter);
}

export function toSet<T>(
  value: unknown,
  converter: (item: unknown) => T,
): Set<T> {
  return new Set(parseCollection(value).map(converter));
}

export function toMap<T>(
  value: unknown,
  converter: (value: unknown) => T,
): Readonly<Record<string, T>> {
  let source = value;
  if (typeof source === "string" && source.trim().startsWith("{")) {
    try {
      source = JSON.parse(source);
    } catch {
      return {};
    }
  }
  if (!isJsonRecord(source)) {
    return {};
  }
  const result: Record<string, T> = {};
  for (const [key, mapValue] of Object.entries(source)) {
    result[key] = converter(mapValue);
  }
  return result;
}

export function asJsonRecord(value: unknown): JsonRecord {
  return isJsonRecord(value) ? value : {};
}
