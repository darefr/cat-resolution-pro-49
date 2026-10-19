import type { Metadata, Viewport } from 'next'
import { Barlow, Barlow_Condensed } from 'next/font/google'
import './globals.css'

const bodyFont = Barlow({ subsets: ['latin'], weight: ['400', '500', '600', '700'], variable: '--font-body', display: 'swap' })
const displayFont = Barlow_Condensed({ subsets: ['latin'], weight: ['600', '700', '800', '900'], style: ['normal', 'italic'], variable: '--font-display', display: 'swap' })

export const metadata: Metadata = {
  title: 'Cat Resolution — Take control of your display',
  description: 'An Android display controller with Shizuku-powered resolution scaling, custom presets, floating controls, and timed recovery. Explore the interface and get the Android app.',
  icons: { icon: '/images/cat-emblem.png', apple: '/images/cat-emblem.png' },
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  colorScheme: 'light',
  themeColor: '#ffffff',
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={`light bg-background ${bodyFont.variable} ${displayFont.variable}`}>
      <body className="font-sans antialiased">{children}</body>
    </html>
  )
}
