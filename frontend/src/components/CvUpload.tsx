"use client";

import { useCallback, useRef, useState } from "react";
import { cvApi } from "@/lib/api";

/**
 * CV input: drag-and-drop (or browse) a PDF / Word .docx. The file's text is
 * extracted on the backend and reported through `onChange`. No plain-text paste.
 */
export default function CvUpload({
  value,
  onChange,
  fileName,
}: {
  value: string;
  onChange: (text: string, fileName: string) => void;
  fileName?: string | null;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleFile = useCallback(
    async (file: File) => {
      setError(null);
      if (!/\.(pdf|docx)$/i.test(file.name)) {
        setError("Please upload a PDF or Word (.docx) document.");
        return;
      }
      if (file.size > 4 * 1024 * 1024) {
        setError("That file is larger than 4 MB — please upload a smaller CV.");
        return;
      }
      setBusy(true);
      try {
        const res = await cvApi.extract(file);
        onChange(res.data.text, file.name);
        setLoaded(file.name);
      } catch (err: unknown) {
        const ax = err as { response?: { data?: { detail?: string } } };
        setError(ax.response?.data?.detail || "Could not read that document. Try another file.");
        onChange("", "");
        setLoaded(null);
      } finally {
        setBusy(false);
      }
    },
    [onChange]
  );

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragging(false);
      const f = e.dataTransfer.files?.[0];
      if (f) handleFile(f);
    },
    [handleFile]
  );

  // A freshly-uploaded name wins; otherwise show the one already on file.
  const displayName = loaded || fileName || null;
  const hasCv = (!!value && value.trim().length > 0) || !!displayName;

  return (
    <div>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            inputRef.current?.click();
          }
        }}
        role="button"
        tabIndex={0}
        aria-label="Upload your CV"
        className={`cursor-pointer rounded-lg border-2 border-dashed px-4 py-6 text-center transition ${
          dragging
            ? "border-indigo-500 bg-indigo-50"
            : hasCv
            ? "border-green-300 bg-green-50 hover:border-green-400"
            : "border-gray-300 hover:border-indigo-400 hover:bg-gray-50"
        }`}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) handleFile(f);
            e.target.value = "";
          }}
        />
        {busy ? (
          <span className="text-sm text-indigo-600">Reading your document…</span>
        ) : hasCv ? (
          <span className="text-sm text-green-700">
            <span className="font-semibold">✓ CV on file</span>
            {displayName ? ` — ${displayName}` : ""}. <span className="underline">Drop a new file to replace.</span>
          </span>
        ) : (
          <span className="text-sm text-gray-500">
            <span className="font-semibold text-indigo-600">Drag &amp; drop</span> your CV here, or{" "}
            <span className="font-semibold text-indigo-600">browse</span> — PDF or Word (.docx)
          </span>
        )}
      </div>
      {error && <p className="text-xs text-red-600 mt-1.5">{error}</p>}
    </div>
  );
}
