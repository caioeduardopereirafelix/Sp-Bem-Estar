// Preenche o responsável e o contato do encarregado, que mudam a cada instalação (PRIVACY_CONTROLLER e PRIVACY_CONTACT)
fetch('/api/v1/privacy')
    .then((response) => (response.ok ? response.json() : null))
    .then((privacy) => {
        if (privacy?.controller) {
            document.querySelector('[data-controller]').textContent = privacy.controller;
        }
        const contact = privacy?.contact;
        if (contact) {
            const target = document.querySelector('[data-contact]');
            const link = document.createElement('a');
            const isEmail = /^[^\s@]+@[^\s@]+$/.test(contact);
            // só e-mail ou https: um valor configurado errado não vira um link javascript:
            if (isEmail || /^https:\/\//.test(contact)) {
                link.href = isEmail ? `mailto:${contact}` : contact;
                link.textContent = contact;
                target.replaceChildren(link);
            } else {
                target.textContent = contact;
            }
        }
    })
    .catch(() => {
        // sem a API, a página continua legível com o texto padrão
    });
