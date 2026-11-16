import { useT } from '../../shared/i18n'
export function LoadState({ error, retry }: { error?: string; retry: () => void }) { const t = useT();
  return <div className="load-state" role={error ? 'alert' : 'status'}><p>{error ? t(error) : t('Loading products…')}</p>
    {error && <button className="button primary" onClick={retry}>{t("Try again")}</button>}</div>
}
