import { useDispatch, useSelector, type TypedUseSelectorHook } from 'react-redux'
import type { RootState, AppDispatch } from '@/app/store'

/** Tipli Redux hook'ları — bileşenlerde `useDispatch`/`useSelector` yerine bunları kullan. */
export const useAppDispatch = () => useDispatch<AppDispatch>()
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector
