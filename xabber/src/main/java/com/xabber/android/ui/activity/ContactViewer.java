/**
 * Copyright (c) 2013, Redsolution LTD. All rights reserved.
 *
 * This file is part of Xabber project; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License, Version 3.
 *
 * Xabber is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License,
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.xabber.android.ui.activity;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.NavUtils;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.xabber.android.R;
import com.xabber.android.data.Application;
import com.xabber.android.data.LogManager;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.account.listeners.OnAccountChangedListener;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.BaseEntity;
import com.xabber.android.data.entity.UserJid;
import com.xabber.android.data.extension.muc.MUCManager;
import com.xabber.android.data.extension.vcard.VCardManager;
import com.xabber.android.data.intent.AccountIntentBuilder;
import com.xabber.android.data.intent.EntityIntentBuilder;
import com.xabber.android.data.roster.AbstractContact;
import com.xabber.android.data.roster.OnContactChangedListener;
import com.xabber.android.data.roster.RosterContact;
import com.xabber.android.data.roster.RosterManager;
import com.xabber.android.ui.color.ColorManager;
import com.xabber.android.ui.color.StatusBarPainter;
import com.xabber.android.ui.fragment.ConferenceInfoFragment;
import com.xabber.android.ui.fragment.ContactVcardViewerFragment;
import com.xabber.android.ui.helper.ContactTitleInflater;

import java.util.Collection;
import java.util.List;

public class ContactViewer extends ManagedActivity implements
        OnContactChangedListener, OnAccountChangedListener, ContactVcardViewerFragment.Listener {

    private AccountJid account;
    private UserJid user;
    private Toolbar toolbar;
    private View contactTitleView;
    private AbstractContact bestContact;
    private CollapsingToolbarLayout collapsingToolbar;

    public static Intent createIntent(Context context, AccountJid account, UserJid user) {
        return new EntityIntentBuilder(context, ContactViewer.class)
                .setAccount(account).setUser(user).build();
    }

    private static AccountJid getAccount(Intent intent) {
        return AccountIntentBuilder.getAccount(intent);
    }

    private static UserJid getUser(Intent intent) {
        return EntityIntentBuilder.getUser(intent);
    }

    protected Toolbar getToolbar() {
        return toolbar;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            // View information about contact from system contact list
            Uri data = getIntent().getData();
            if (data != null && "content".equals(data.getScheme())) {
                List<String> segments = data.getPathSegments();
                if (segments.size() == 2 && "data".equals(segments.get(0))) {
                    Long id;
                    try {
                        id = Long.valueOf(segments.get(1));
                    } catch (NumberFormatException e) {
                        id = null;
                    }
                    if (id != null)
                        // FIXME: Will be empty while application is loading
                        for (RosterContact rosterContact : RosterManager.getInstance().getContacts())
                            if (id.equals(rosterContact.getViewId())) {
                                account = rosterContact.getAccount();
                                user = rosterContact.getUser();
                                break;
                            }
                }
            }
        } else {
            account = getAccount(getIntent());
            user = getUser(getIntent());
        }

        if (user != null && user.getBareJid().equals(account.getFullJid().asBareJid())) {
            try {
                user = UserJid.from(AccountManager.getInstance().getAccount(account).getRealJid().asBareJid());
            } catch (UserJid.UserJidCreateException e) {
                LogManager.exception(this, e);
            }
        }

        if (account == null || user == null) {
            Application.getInstance().onError(R.string.ENTRY_IS_NOT_FOUND);
            finish();
            return;
        }

        setContentView(R.layout.contact_viewer);

        if (savedInstanceState == null) {

            Fragment fragment;
            if (MUCManager.getInstance().hasRoom(account, user)) {
                fragment = ConferenceInfoFragment.newInstance(account, user.getJid().asEntityBareJidIfPossible());
            } else {
                fragment = ContactVcardViewerFragment.newInstance(account, user);
            }

            getFragmentManager().beginTransaction().add(R.id.scrollable_container, fragment).commit();


        }

        bestContact = RosterManager.getInstance().getBestContact(account, user);

        toolbar = (Toolbar) findViewById(R.id.toolbar_default);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white_24dp);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavUtils.navigateUpFromSameTask(ContactViewer.this);
            }
        });


        StatusBarPainter statusBarPainter = new StatusBarPainter(this);
        statusBarPainter.updateWithAccountName(account);

        final int accountMainColor = ColorManager.getInstance().getAccountPainter().getAccountMainColor(account);

        contactTitleView = findViewById(R.id.contact_title_expanded);
        findViewById(R.id.status_icon).setVisibility(View.GONE);
        contactTitleView.setBackgroundColor(accountMainColor);
        TextView contactNameView = (TextView) findViewById(R.id.name);
        contactNameView.setVisibility(View.INVISIBLE);

        collapsingToolbar = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        collapsingToolbar.setTitle(bestContact.getName());

        collapsingToolbar.setBackgroundColor(accountMainColor);
        collapsingToolbar.setContentScrimColor(accountMainColor);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Application.getInstance().addUIListener(OnContactChangedListener.class, this);
        Application.getInstance().addUIListener(OnAccountChangedListener.class, this);
        ContactTitleInflater.updateTitle(contactTitleView, this, bestContact);
        updateName();
    }

    @Override
    public void onPause() {
        super.onPause();
        Application.getInstance().removeUIListener(OnContactChangedListener.class, this);
        Application.getInstance().removeUIListener(OnAccountChangedListener.class, this);
    }

    @Override
    public void onContactsChanged(Collection<BaseEntity> entities) {
        for (BaseEntity entity : entities) {
            if (entity.equals(account, user)) {
                updateName();
                break;
            }
        }
    }

    private void updateName() {
        if (MUCManager.getInstance().isMucPrivateChat(account, user)) {
            String vCardName = VCardManager.getInstance().getName(user.getJid());
            if (!"".equals(vCardName)) {
                collapsingToolbar.setTitle(vCardName);
            } else {
                collapsingToolbar.setTitle(user.getJid().getResourceOrNull().toString());
            }

        } else {
            collapsingToolbar.setTitle(RosterManager.getInstance().getBestContact(account, user).getName());
        }
    }

    @Override
    public void onAccountsChanged(Collection<AccountJid> accounts) {
        if (accounts.contains(account)) {
            updateName();
        }
    }

    protected AccountJid getAccount() {
        return account;
    }

    protected UserJid getUser() {
        return user;
    }

    @Override
    public void onVCardReceived() {
        ContactTitleInflater.updateTitle(contactTitleView, this, bestContact);
    }
}
