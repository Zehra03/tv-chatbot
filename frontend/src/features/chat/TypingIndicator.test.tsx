import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { TypingIndicator } from '@/features/chat/TypingIndicator'

afterEach(cleanup)

describe('TypingIndicator', () => {
  it('varsayılan: "Asistan yazıyor…" durumunu duyurur', () => {
    render(<TypingIndicator />)
    expect(screen.getByRole('status')).toBeTruthy()
    expect(screen.getByText('Asistan yazıyor…')).toBeTruthy()
    expect(screen.queryByText('Arıyorum…')).toBeNull()
  })

  it('searching: görünür "Arıyorum…" etiketi gösterir', () => {
    render(<TypingIndicator searching />)
    expect(screen.getByRole('status')).toBeTruthy()
    expect(screen.getByText('Arıyorum…')).toBeTruthy()
    expect(screen.queryByText('Asistan yazıyor…')).toBeNull()
  })
})
