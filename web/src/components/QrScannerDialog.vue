<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import jsQR from 'jsqr'

import BaseModal from './BaseModal.vue'

/**
 * Сканирование QR камерой (`docs/architecture.md` §4): сначала системный
 * `BarcodeDetector`, при его отсутствии — `jsQR` по кадру с видео.
 */

interface DetectedBarcode {
  rawValue: string
}

interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<DetectedBarcode[]>
}

type BarcodeDetectorCtor = new (options?: { formats?: string[] }) => BarcodeDetectorLike

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (event: 'close'): void; (event: 'detected', value: string): void }>()

const video = ref<HTMLVideoElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const error = ref<string | null>(null)
const scanning = ref(false)

let stream: MediaStream | null = null
let timer: ReturnType<typeof setTimeout> | null = null
let detector: BarcodeDetectorLike | null = null

function stop(): void {
  scanning.value = false
  if (timer !== null) {
    clearTimeout(timer)
    timer = null
  }
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
  if (video.value) {
    video.value.srcObject = null
  }
}

async function start(): Promise<void> {
  error.value = null
  if (!navigator.mediaDevices?.getUserMedia) {
    error.value = 'Камера недоступна в этом браузере. Введите код приглашения вручную.'
    return
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment' },
      audio: false,
    })
    if (video.value) {
      video.value.srcObject = stream
      await video.value.play()
    }
    const ctor = (window as unknown as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector
    detector = ctor ? new ctor({ formats: ['qr_code'] }) : null
    scanning.value = true
    void tick()
  } catch (caught) {
    console.warn('[dnd-memory] камера недоступна', caught)
    error.value = 'Не удалось получить доступ к камере. Разрешите доступ или введите код вручную.'
    scanning.value = false
  }
}

async function tick(): Promise<void> {
  if (!scanning.value) return
  const videoEl = video.value
  const canvasEl = canvas.value
  if (videoEl && canvasEl && videoEl.readyState >= videoEl.HAVE_CURRENT_DATA) {
    const width = videoEl.videoWidth
    const height = videoEl.videoHeight
    if (width > 0 && height > 0) {
      canvasEl.width = width
      canvasEl.height = height
      const context = canvasEl.getContext('2d', { willReadFrequently: true })
      if (context) {
        context.drawImage(videoEl, 0, 0, width, height)
        let value: string | null = null
        if (detector) {
          try {
            const codes = await detector.detect(canvasEl)
            value = codes[0]?.rawValue ?? null
          } catch {
            value = null
          }
        }
        if (!value) {
          const image = context.getImageData(0, 0, width, height)
          value = jsQR(image.data, width, height, { inversionAttempts: 'attemptBoth' })?.data ?? null
        }
        if (value) {
          stop()
          emit('detected', value)
          return
        }
      }
    }
  }
  timer = setTimeout(() => void tick(), 300)
}

watch(
  () => props.open,
  (open) => {
    if (open) void start()
    else stop()
  },
)

onBeforeUnmount(stop)
</script>

<template>
  <BaseModal :open="open" title="Сканирование QR-кода" @close="emit('close')">
    <div class="stack">
      <video ref="video" class="video-frame" playsinline muted />
      <canvas ref="canvas" hidden />
      <p v-if="error" class="notice notice--danger">{{ error }}</p>
      <p v-else class="faint">Наведите камеру на QR-код приглашения.</p>
    </div>
    <template #actions>
      <button class="btn" type="button" @click="emit('close')">Закрыть</button>
    </template>
  </BaseModal>
</template>
