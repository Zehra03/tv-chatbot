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

describe('AnimatedAIChat — komut paleti erişilebilirliği', () => {
  it('komut düğmesinin erişilebilir adı var ve durumunu duyurur', async () => {
    const user = userEvent.setup()
    render(<AnimatedAIChat onSend={vi.fn()} />)

    // Regresyonda bu düğme yalnız bir ikondu: ekran okuyucu "button" diyordu.
    const button = screen.getByRole('button', { name: 'Komutlar' })
    expect(button.getAttribute('aria-expanded')).toBe('false')

    await user.click(button)
    expect(button.getAttribute('aria-expanded')).toBe('true')
    expect(screen.getByRole('listbox', { name: 'Sohbet komutları' })).toBeTruthy()
  })

  it('ok tuşuyla gezilen komut aria-activedescendant ile duyurulur', async () => {
    const user = userEvent.setup()
    render(<AnimatedAIChat onSend={vi.fn()} />)
    const input = screen.getByLabelText('Mesaj')

    await user.click(input)
    await user.keyboard('/')
    // Palet açık: seçenekler gerçek role="option" ve textarea combobox gibi davranır.
    const options = screen.getAllByRole('option')
    expect(options.length).toBeGreaterThan(0)
    expect(input.getAttribute('aria-expanded')).toBe('true')

    await user.keyboard('{ArrowDown}')
    const active = input.getAttribute('aria-activedescendant')
    expect(active).toBeTruthy()
    // Duyurulan id GERÇEKTEN seçili seçeneğe işaret etmeli.
    const selected = options.find((o) => o.getAttribute('aria-selected') === 'true')
    expect(selected?.id).toBe(active)
  })

  it('eşleşen komut yokken Enter mesajı gönderir — yutmaz', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<AnimatedAIChat onSend={onSend} />)

    await user.click(screen.getByLabelText('Mesaj'))
    // '/' ile başlar → palet açılır, ama hiçbir komut '/merhaba' ile eşleşmez.
    await user.paste('/merhaba')
    await user.keyboard('{Enter}')

    // Regresyonda: preventDefault + activeSuggestion === -1 → hiçbir şey olmuyordu.
    expect(onSend).toHaveBeenCalledWith('/merhaba')
  })

  it('eşleşen komut varken Enter komutu seçer — mesajı göndermez', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<AnimatedAIChat onSend={onSend} />)

    await user.click(screen.getByLabelText('Mesaj'))
    await user.keyboard('/')
    await user.keyboard('{ArrowDown}')
    await user.keyboard('{Enter}')

    expect(onSend).not.toHaveBeenCalled()
  })
})
