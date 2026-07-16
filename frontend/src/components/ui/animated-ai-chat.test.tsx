import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnimatedAIChat } from '@/components/ui/animated-ai-chat'

afterEach(cleanup)

/**
 * ChatPage'in gerçekte render ettiği composer burasıdır (features/chat/Composer değil),
 * bu yüzden karakter sınırı testleri de burada yaşar.
 */
describe('AnimatedAIChat — karakter sınırı', () => {
  it('sayaç yalnız 1800 karakterden sonra görünür', async () => {
    const user = userEvent.setup()
    render(<AnimatedAIChat onSend={vi.fn()} />)
    const input = screen.getByLabelText('Mesaj')

    await user.click(input)
    await user.paste('x'.repeat(1799))
    expect(screen.queryByText(/\/2000$/)).toBeNull()

    await user.paste('x')
    expect(screen.getByText('1800/2000')).toBeTruthy()
  })

  it('2000 karakteri aşan yapıştırmayı kırpar ve gönderilen metin de kırpılmış olur', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<AnimatedAIChat onSend={onSend} />)

    const input = screen.getByLabelText('Mesaj') as HTMLTextAreaElement
    await user.click(input)
    await user.paste('x'.repeat(2500))

    expect(input.value.length).toBe(2000)
    expect(screen.getByText('2000/2000')).toBeTruthy()

    await user.click(screen.getByRole('button', { name: /gönder/i }))
    expect(onSend).toHaveBeenCalledWith('x'.repeat(2000))
  })

  it('kısa mesajda sayaç görünmez', async () => {
    const user = userEvent.setup()
    render(<AnimatedAIChat onSend={vi.fn()} />)

    await user.click(screen.getByLabelText('Mesaj'))
    await user.paste('Antalya oteli arıyorum')
    expect(screen.queryByText(/\/2000$/)).toBeNull()
  })
})
