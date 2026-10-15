import { toDataURL } from 'qrcode'

/**
 * QR-код приглашения (`docs/architecture.md` §4): генерируется на клиенте,
 * поэтому работает и без сети. Возвращается data URL изображения PNG.
 */
export async function generateQrDataUrl(
  text: string,
  options: { width?: number; margin?: number } = {},
): Promise<string> {
  return toDataURL(text, {
    width: options.width ?? 512,
    margin: options.margin ?? 1,
    errorCorrectionLevel: 'M',
    color: { dark: '#12141aff', light: '#ffffffff' },
  })
}
