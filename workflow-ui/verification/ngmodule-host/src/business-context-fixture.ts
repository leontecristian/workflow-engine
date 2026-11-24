import type { TaskInput } from '@workflow-engine/ui';
import { findNodeAtLocation, parseTree } from 'jsonc-parser';

// F037 revision 1. The review inputs must reach the component with their original tokens.
export const bookingExample = String.raw`{
  "inputs": {
    "bookingDetailsAtSubmission": {
      "type": "JSON",
      "value": {
        "booking": {
          "client": "ENEL SERVICII COMUNE",
          "priority": "D",
          "project": "Consolidation test Test KB Docs3 cc"
        },
        "personnel": {
          "employee": "Ori Efraim Ofir",
          "engagementManager": "Cezar Gabriel Furtuna",
          "phase": "Interim",
          "position": "Partner"
        },
        "schedule": {
          "durationHours": 8.000000000000000,
          "endDate": "2026-11-20",
          "lastUpdated": "2026-11-20 18:55:16",
          "startDate": "2026-11-20",
          "updatedBy": "atarassoff"
        }
      }
    },
    "bookingReference": {
      "type": "STRING",
      "value": "21685"
    }
  }
}`;

const example = parseTree(bookingExample)!;
const inputNodes = findNodeAtLocation(example, ['inputs'])!.children!;
const bookingInputs = Object.freeze(Object.fromEntries(inputNodes.map((input) => {
  const [key, entry] = input.children!;
  const type: TaskInput['type'] = findNodeAtLocation(entry, ['type'])!.value;
  const { offset, length } = findNodeAtLocation(entry, ['value'])!;
  return [key.value, Object.freeze({ type, valueJson: bookingExample.slice(offset, offset + length) })];
})));

const scalarInputs = Object.freeze({
  firstCode: Object.freeze({ type: 'STRING', valueJson: '"008.00"' }),
  secondFlag: Object.freeze({ type: 'BOOLEAN', valueJson: 'false' }),
} satisfies Record<string, TaskInput>);

const genericInputs = Object.freeze({
  calibration: Object.freeze({
    type: 'JSON',
    valueJson: String.raw`{
      "readings": {
        "offset": 9007199254740993.01000,
        "multiplier": 1.2300E+05,
        "units": 0,
        "disabled": false,
        "note": "",
        "owner": null,
        "attributes": {},
        "tags": [],
        "device": {"serial": "a-very-long-unbroken-device-identifier-00000000000000000000000000000000000000000000000000"}
      },
      "samples": [
        {"position": "first", "note": "first line\nsecond line"},
        {"position": "second", "note": "<img src=x>"}
      ]
    }`,
  }),
  firstCode: scalarInputs.firstCode,
  broken: Object.freeze({ type: 'JSON', valueJson: '{"partiallyReadable":1,' }),
  secondFlag: scalarInputs.secondFlag,
  limits: Object.freeze({ type: 'JSON', valueJson: '{"maximum":5.00}' }),
} satisfies Record<string, TaskInput>);

export function businessContextInputs(mode: string): Readonly<Record<string, TaskInput>> {
  switch (mode) {
    case 'scalar': return scalarInputs;
    case 'generic': return genericInputs;
    default: return bookingInputs;
  }
}
