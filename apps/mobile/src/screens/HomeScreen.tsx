import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { CameraView, useCameraPermissions } from "expo-camera";
import type { HBNCVisit, IncentiveClaim } from "@asha-g/shared-types";
import { cameraToStructuredData, textToStructuredData } from "../features/ocr/pipeline";
import { parseRegisterText } from "../features/ocr/parser";
import { askAshabotFromText } from "../features/rag/ashabot";
import { computeRisk, scheduleHbncVisits } from "../features/hbnc/scheduler";
import { submitIncentiveDraft } from "../features/incentives/ledger";
import { runSyncOnce } from "../sync/engine";
import { getPendingOutboxCount, listClaims, listDigitizedRecords, listVisits, type DigitizedRecord } from "../db/repository";
import { isDeviceOcrAvailable } from "../services/ocr/deviceOcr";
import { cancelSpeechToText, isSttAvailable, startSpeechToText, stopSpeechToText } from "../services/stt/localStt";
import { localTts, stopTts } from "../services/tts/localTts";

const ASHA_ID = "asha-001";
const EXPECTED_FIELDS = ["name", "age", "village", "lmp", "edd", "hb", "bp", "weight", "newborn_weight"] as const;

function Button(props: { label: string; onPress: () => void | Promise<void>; disabled?: boolean }) {
  return (
    <TouchableOpacity style={[styles.button, props.disabled ? styles.buttonDisabled : null]} onPress={props.onPress} disabled={props.disabled}>
      <Text style={styles.buttonText}>{props.label}</Text>
    </TouchableOpacity>
  );
}

function Section(props: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{props.title}</Text>
      {props.children}
    </View>
  );
}

function parseTextToMap(input: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const line of input.split(/\r?\n/)) {
    const [k, ...rest] = line.split(":");
    if (!k || rest.length === 0) continue;
    out[k.trim().toLowerCase()] = rest.join(":").trim();
  }
  return out;
}

function mapToText(map: Record<string, string>): string {
  return EXPECTED_FIELDS.map((k) => `${k}: ${map[k] ?? ""}`).join("\n");
}

function detectMissingFields(text: string): string[] {
  const parsed = parseRegisterText(text);
  return Object.entries(parsed)
    .filter(([, value]) => value === null || String(value).trim() === "")
    .map(([key]) => key);
}

function transcriptToFields(transcript: string): Record<string, string> {
  const t = transcript.toLowerCase();
  const fields: Record<string, string> = {};

  const read = (key: string, regex: RegExp) => {
    const m = t.match(regex);
    if (m?.[1]) fields[key] = m[1].trim();
  };

  read("name", /(?:name|patient name|mother name)\s*(?:is)?\s*([a-z ]{2,})/i);
  read("age", /(?:age)\s*(?:is)?\s*(\d{1,2})/i);
  read("village", /(?:village|gaon)\s*(?:is)?\s*([a-z ]{2,})/i);
  read("lmp", /(?:lmp)\s*(?:is)?\s*([0-9\/-]{6,12})/i);
  read("edd", /(?:edd|delivery date)\s*(?:is)?\s*([0-9\/-]{6,12})/i);
  read("hb", /(?:hb|hemoglobin)\s*(?:is)?\s*([0-9]+(?:\.[0-9]+)?)/i);
  read("bp", /(?:bp|blood pressure)\s*(?:is)?\s*([0-9]{2,3}(?:\/[0-9]{2,3})?)/i);
  read("weight", /(?:weight|mother weight)\s*(?:is)?\s*([0-9]+(?:\.[0-9]+)?)/i);
  read("newborn_weight", /(?:newborn weight|baby weight)\s*(?:is)?\s*([0-9]+(?:\.[0-9]+)?)/i);

  return fields;
}

export function HomeScreen() {
  const cameraRef = useRef<CameraView | null>(null);
  const [permission, requestPermission] = useCameraPermissions();
  const [cameraOpen, setCameraOpen] = useState(false);

  const [registerText, setRegisterText] = useState("name: Sita\nvillage: Rampur\nnewborn_weight: 1.7");
  const [digitizedPreview, setDigitizedPreview] = useState("");
  const [missingFields, setMissingFields] = useState<string[]>(detectMissingFields("name: Sita\nvillage: Rampur\nnewborn_weight: 1.7"));
  const [records, setRecords] = useState<DigitizedRecord[]>([]);

  const [speechTranscript, setSpeechTranscript] = useState("");
  const [sttStatus, setSttStatus] = useState("Not listening");
  const [isListening, setIsListening] = useState(false);

  const [question, setQuestion] = useState("What are danger signs for a newborn?");
  const [botAnswer, setBotAnswer] = useState("Ask a question to get protocol guidance.");

  const [birthDate, setBirthDate] = useState("2026-02-10");
  const [newbornCaseId, setNewbornCaseId] = useState("case-001");
  const [weightInput, setWeightInput] = useState("1.7");
  const [visits, setVisits] = useState<HBNCVisit[]>([]);

  const [claims, setClaims] = useState<IncentiveClaim[]>([]);
  const [pendingSyncCount, setPendingSyncCount] = useState(0);
  const [lastSyncResult, setLastSyncResult] = useState("Not synced yet");

  const risk = useMemo(() => computeRisk(Number(weightInput)), [weightInput]);
  const ocrAvailable = useMemo(() => isDeviceOcrAvailable(), []);
  const sttAvailable = useMemo(() => isSttAvailable(), []);

  const refresh = useCallback(async () => {
    const [r, v, c, pending] = await Promise.all([
      listDigitizedRecords(),
      listVisits(),
      listClaims(ASHA_ID),
      getPendingOutboxCount()
    ]);
    setRecords(r);
    setVisits(v);
    setClaims(c);
    setPendingSyncCount(pending);
  }, []);

  useEffect(() => {
    refresh();

    return () => {
      cancelSpeechToText();
      stopTts();
    };
  }, [refresh]);

  const captureAndOcr = useCallback(async () => {
    const photo = await cameraRef.current?.takePictureAsync({ quality: 0.7 });
    if (!photo?.uri) return;

    const out = await cameraToStructuredData(photo.uri);
    setRegisterText(out.ocrText);
    setDigitizedPreview(JSON.stringify(out.json, null, 2));
    setMissingFields(detectMissingFields(out.ocrText));
    await refresh();
    setCameraOpen(false);
  }, [refresh]);

  return (
    <ScrollView style={styles.root} contentContainerStyle={styles.content}>
      <Text style={styles.heading}>Sakhi</Text>
      <Text style={styles.subHeading}>Offline-first support for documentation, guidance, schedules, and incentives.</Text>

      <Section title="1) Camera OCR + Register Fill">
        <Text style={styles.helperText}>OCR engine: {ocrAvailable ? "Device OCR active" : "Fallback OCR active (install native OCR module for full extraction)"}</Text>

        <View style={styles.row}>
          <View style={styles.rowItem}>
            <Button
              label="Open Camera"
              onPress={async () => {
                if (!permission?.granted) {
                  const res = await requestPermission();
                  if (!res.granted) return;
                }
                setCameraOpen((x) => !x);
              }}
            />
          </View>
          <View style={styles.rowItem}>
            <Button
              label="Save Structured Record"
              onPress={async () => {
                const out = await textToStructuredData(registerText);
                setDigitizedPreview(JSON.stringify(out.json, null, 2));
                setMissingFields(detectMissingFields(registerText));
                await refresh();
              }}
            />
          </View>
        </View>

        {cameraOpen ? (
          <View style={styles.cameraWrap}>
            <CameraView ref={cameraRef} style={styles.camera} facing="back" />
            <View style={styles.row}>
              <View style={styles.rowItem}>
                <Button label="Capture + OCR" onPress={captureAndOcr} />
              </View>
              <View style={styles.rowItem}>
                <Button label="Close" onPress={() => setCameraOpen(false)} />
              </View>
            </View>
          </View>
        ) : null}

        <TextInput
          style={styles.textArea}
          multiline
          value={registerText}
          onChangeText={(value) => {
            setRegisterText(value);
            setMissingFields(detectMissingFields(value));
          }}
          placeholder="OCR text or manual data"
        />

        <Text style={styles.miniTitle}>Missing Fields</Text>
        <Text style={styles.preview}>{missingFields.length ? missingFields.join(", ") : "None"}</Text>

        <Text style={styles.miniTitle}>Voice Fill Missing Fields</Text>
        <Text style={styles.helperText}>
          {sttAvailable
            ? "Tap Start STT and speak: 'name Sita, age 23, village Rampur, hb 11.2, bp 120/80'"
            : "STT module not available in current build. Use keyboard mic in transcript box below."}
        </Text>

        <View style={styles.row}>
          <View style={styles.rowItem}>
            <Button
              label={isListening ? "Listening..." : "Start STT"}
              disabled={isListening}
              onPress={async () => {
                setSttStatus("Listening...");
                const started = await startSpeechToText({
                  onResult: (text, isFinal) => {
                    setSpeechTranscript(text);
                    if (isFinal) {
                      setIsListening(false);
                      setSttStatus("Final transcript captured");
                    }
                  },
                  onError: (message) => {
                    setIsListening(false);
                    setSttStatus(message);
                  }
                });
                setIsListening(started);
              }}
            />
          </View>
          <View style={styles.rowItem}>
            <Button
              label="Stop STT"
              onPress={() => {
                stopSpeechToText();
                setIsListening(false);
                setSttStatus("Stopped");
              }}
            />
          </View>
        </View>

        <TextInput
          style={styles.textArea}
          multiline
          value={speechTranscript}
          onChangeText={setSpeechTranscript}
          placeholder="Speech transcript appears here (or type/paste with keyboard mic)"
        />

        <Button
          label="Apply Voice Fill"
          onPress={() => {
            const parsed = parseTextToMap(registerText);
            const fromSpeech = transcriptToFields(speechTranscript);

            for (const missing of missingFields) {
              if (fromSpeech[missing]) parsed[missing] = fromSpeech[missing];
            }

            const merged = mapToText(parsed);
            setRegisterText(merged);
            setMissingFields(detectMissingFields(merged));
            setSttStatus("Applied transcript to missing fields");
          }}
        />

        <Text style={styles.helperText}>STT status: {sttStatus}</Text>

        <Text style={styles.miniTitle}>Latest Parsed Record</Text>
        <Text style={styles.preview}>{digitizedPreview || "No record parsed yet."}</Text>
        <Text style={styles.miniTitle}>Saved Records: {records.length}</Text>
      </Section>

      <Section title="2) ASHABot Clinical Guidance">
        <TextInput style={styles.input} value={question} onChangeText={setQuestion} placeholder="Ask clinical question" />
        <View style={styles.row}>
          <View style={styles.rowItem}>
            <Button
              label="Get Guidance"
              onPress={async () => {
                const answer = await askAshabotFromText(question);
                setBotAnswer(answer);
              }}
            />
          </View>
          <View style={styles.rowItem}>
            <Button label="Speak Answer" onPress={() => localTts(botAnswer, "hi-IN")} />
          </View>
        </View>
        <Button label="Stop Speaking" onPress={() => stopTts()} />
        <Text style={styles.preview}>{botAnswer}</Text>
      </Section>

      <Section title="3) HBNC Scheduler + Risk">
        <TextInput style={styles.input} value={newbornCaseId} onChangeText={setNewbornCaseId} placeholder="Newborn case ID" />
        <TextInput style={styles.input} value={birthDate} onChangeText={setBirthDate} placeholder="Birth date YYYY-MM-DD" />
        <TextInput style={styles.input} value={weightInput} onChangeText={setWeightInput} placeholder="Birth weight in kg" />
        <Text style={styles.risk}>Risk: {risk.riskLevel} {risk.sncuReferralRequired ? "(Immediate SNCU referral)" : ""}</Text>
        <Button
          label="Generate HBNC Visits"
          onPress={async () => {
            await scheduleHbncVisits(newbornCaseId, birthDate);
            await refresh();
          }}
        />
        <Text style={styles.miniTitle}>Scheduled Visits ({visits.length})</Text>
        {visits.slice(-6).map((v) => (
          <Text key={v.id} style={styles.listItem}>Day {v.visitDay} - {v.dueDate}</Text>
        ))}
      </Section>

      <Section title="4) Incentive Ledger">
        <View style={styles.row}>
          <View style={styles.rowItem}>
            <TouchableOpacity
              style={styles.smallButton}
              onPress={async () => {
                await submitIncentiveDraft({ ashaId: ASHA_ID, serviceType: "ANC" });
                await refresh();
              }}
            >
              <Text style={styles.smallButtonText}>Add ANC ₹300</Text>
            </TouchableOpacity>
          </View>
          <View style={styles.rowItem}>
            <TouchableOpacity
              style={styles.smallButton}
              onPress={async () => {
                await submitIncentiveDraft({ ashaId: ASHA_ID, serviceType: "HBNC_COMPLETION" });
                await refresh();
              }}
            >
              <Text style={styles.smallButtonText}>Add HBNC ₹250</Text>
            </TouchableOpacity>
          </View>
        </View>
        <Text style={styles.miniTitle}>Claims ({claims.length})</Text>
        {claims.slice(0, 6).map((c) => (
          <Text key={c.id} style={styles.listItem}>{c.serviceType} - ₹{c.amountRupees} - {c.status}</Text>
        ))}
      </Section>

      <Section title="5) Sync Queue">
        <Text style={styles.listItem}>Pending offline actions: {pendingSyncCount}</Text>
        <Button
          label="Sync Now"
          onPress={async () => {
            const result = await runSyncOnce();
            setLastSyncResult(`ACK ${result.ack}, FAILED ${result.failed}`);
            await refresh();
          }}
        />
        <Text style={styles.preview}>{lastSyncResult}</Text>
      </Section>

      <View style={styles.footerBox}>
        <Text style={styles.footerText}>Privacy by default: no background location tracking.</Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#fff" },
  content: { padding: 16, paddingTop: 40, paddingBottom: 40 },
  heading: { fontSize: 28, fontWeight: "800", color: "#1f1f1f" },
  subHeading: { marginTop: 6, marginBottom: 14, color: "#555", lineHeight: 20 },
  helperText: { color: "#555", marginBottom: 8 },
  section: {
    backgroundColor: "#fffaf4",
    borderWidth: 1,
    borderColor: "#efd9bf",
    borderRadius: 12,
    padding: 12,
    marginBottom: 12
  },
  sectionTitle: { fontSize: 17, fontWeight: "700", marginBottom: 8, color: "#2a2a2a" },
  input: {
    borderWidth: 1,
    borderColor: "#d7d7d7",
    borderRadius: 8,
    backgroundColor: "#fff",
    padding: 10,
    marginBottom: 8
  },
  textArea: {
    borderWidth: 1,
    borderColor: "#d7d7d7",
    borderRadius: 8,
    backgroundColor: "#fff",
    padding: 10,
    marginBottom: 8,
    minHeight: 90,
    textAlignVertical: "top"
  },
  cameraWrap: {
    marginBottom: 8,
    borderWidth: 1,
    borderColor: "#d7d7d7",
    borderRadius: 10,
    overflow: "hidden"
  },
  camera: {
    width: "100%",
    height: 260,
    backgroundColor: "#000"
  },
  button: {
    backgroundColor: "#cb6425",
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 12,
    alignItems: "center",
    marginBottom: 8
  },
  buttonDisabled: {
    opacity: 0.5
  },
  buttonText: { color: "#fff", fontWeight: "700" },
  miniTitle: { marginTop: 4, marginBottom: 4, fontWeight: "700", color: "#414141" },
  preview: { color: "#313131", lineHeight: 19 },
  risk: { marginBottom: 8, color: "#7b3607", fontWeight: "700" },
  listItem: { paddingVertical: 2, color: "#333" },
  row: { flexDirection: "row", marginBottom: 8 },
  rowItem: { flex: 1, marginRight: 8 },
  smallButton: {
    backgroundColor: "#e7efe9",
    borderWidth: 1,
    borderColor: "#b9cfbf",
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 10,
    alignItems: "center"
  },
  smallButtonText: { color: "#1f5630", fontWeight: "700" },
  footerBox: {
    marginTop: 4,
    padding: 12,
    borderRadius: 10,
    backgroundColor: "#eef8ef",
    borderWidth: 1,
    borderColor: "#c9e2cb"
  },
  footerText: { color: "#35583a" }
});
