import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Composer } from '@/features/chat/Composer'

afterEach(cleanup)

describe('Composer', () => {
  it('mesajı gönderir, kırpar ve alanı temizler', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<Composer onSend={onSend} />)

    const input = screen.getByLabelText('Mesaj') as HTMLInputElement
    await user.type(input, '  merhaba  ')
    await user.click(screen.getByRole('button', { name: /gönder/i }))

    expect(onSend).toHaveBeenCalledTimes(1)
    expect(onSend).toHaveBeenCalledWith('merhaba')
    expect(input.value).toBe('')
  })

  it('Enter ile gönderir', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<Composer onSend={onSend} />)

    await user.type(screen.getByLabelText('Mesaj'), 'otel arıyorum{Enter}')
    expect(onSend).toHaveBeenCalledTimes(1)
    expect(onSend).toHaveBeenCalledWith('otel arıyorum')
  })

  it('boş/boşluk mesajda göndermez; buton devre dışı kalır', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<Composer onSend={onSend} />)

    const button = screen.getByRole('button', { name: /gönder/i }) as HTMLButtonElement
    expect(button.disabled).toBe(true)

    await user.type(screen.getByLabelText('Mesaj'), '   {Enter}')
    expect(onSend).not.toHaveBeenCalled()
    expect(button.disabled).toBe(true)
  })

  it('disabled iken yazma ve gönderme kilitlidir', async () => {
    const onSend = vi.fn()
    render(<Composer onSend={onSend} disabled />)

    expect((screen.getByLabelText('Mesaj') as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: /gönder/i }) as HTMLButtonElement).disabled).toBe(
      true,
    )
  })

  it('placeholder bekleyen soruyu ipucu olarak taşır', () => {
    render(<Composer onSend={vi.fn()} placeholder="Giriş tarihi nedir?" />)
    expect(screen.getByPlaceholderText('Giriş tarihi nedir?')).toBeTruthy()
  })

  it('sayaç yalnız 1800 karakterden sonra görünür', async () => {
    const user = userEvent.setup()
    render(<Composer onSend={vi.fn()} />)
    const input = screen.getByLabelText('Mesaj')

    await user.click(input)
    await user.paste('x'.repeat(1799))
    expect(screen.queryByText(/\/2000$/)).toBeNull()

    await user.paste('x')
    expect(screen.getByText('1800/2000')).toBeTruthy()
  })

  it('2000 karakteri aşan girdiyi kırpar', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<Composer onSend={onSend} />)

    const input = screen.getByLabelText('Mesaj') as HTMLInputElement
    await user.click(input)
    await user.paste('x'.repeat(2500))

    expect(input.value.length).toBe(2000)
    expect(screen.getByText('2000/2000')).toBeTruthy()

    await user.click(screen.getByRole('button', { name: /gönder/i }))
    expect(onSend).toHaveBeenCalledWith('x'.repeat(2000))
  })
})
