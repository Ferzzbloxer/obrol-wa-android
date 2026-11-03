package obrolcore

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	_ "github.com/mattn/go-sqlite3"
	qrcode "github.com/skip2/go-qrcode"
	"go.mau.fi/whatsmeow"
	waCompanionReg "go.mau.fi/whatsmeow/proto/waCompanionReg"
	waE2E "go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/store"
	"go.mau.fi/whatsmeow/store/sqlstore"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	waLog "go.mau.fi/whatsmeow/util/log"
	"google.golang.org/protobuf/proto"
)

// MobileListener receives events in Kotlin
type MobileListener interface {
	OnQR(pngBytes []byte)
	OnQRTimeout()
	OnConnected()
	OnDisconnected()
	OnStatus(status string)
	OnNewMessage(chatJID, msgID, sender, text string, fromMe bool, ts int64, kind string)
	OnReceipt(chatJID, msgID string, status int)
	OnCall(chatJID string, isVideo bool, phase string)
}

type Client struct {
	cli      *whatsmeow.Client
	st       *Store
	listener MobileListener
	dataDir  string
}

func NewClient(dataDir string, listener MobileListener) (*Client, error) {
	store.SetOSInfo("Obrol (Android)", [3]uint32{1, 7, 0})
	store.DeviceProps.PlatformType = waCompanionReg.DeviceProps_DESKTOP.Enum()

	sessionDB := "file:" + filepath.Join(dataDir, "session.db") + "?_foreign_keys=on&_journal_mode=WAL"
	container, err := sqlstore.New(context.Background(), "sqlite3", sessionDB, waLog.Noop)
	if err != nil {
		return nil, err
	}

	dev, err := container.GetFirstDevice(context.Background())
	if err != nil {
		return nil, err
	}

	st, err := openStore(filepath.Join(dataDir, "app.db"))
	if err != nil {
		return nil, err
	}

	c := &Client{
		cli:      whatsmeow.NewClient(dev, waLog.Noop),
		st:       st,
		listener: listener,
		dataDir:  dataDir,
	}

	c.cli.EnableAutoReconnect = true
	c.cli.AddEventHandler(c.handleEvent)

	return c, nil
}

func (c *Client) Start() {
	if c.cli.Store.ID == nil {
		c.StartQR()
	} else {
		c.listener.OnStatus("Connecting…")
		go func() {
			if err := c.cli.Connect(); err != nil {
				c.listener.OnStatus("Failed: " + err.Error())
			}
		}()
	}
}

func (c *Client) StartQR() {
	go func() {
		if c.cli.IsConnected() {
			c.cli.Disconnect()
		}
		qrChan, err := c.cli.GetQRChannel(context.Background())
		if err != nil {
			c.listener.OnStatus("QR error: " + err.Error())
			return
		}
		if err := c.cli.Connect(); err != nil {
			c.listener.OnStatus("Failed: " + err.Error())
			c.listener.OnQRTimeout()
			return
		}
		for evt := range qrChan {
			switch evt.Event {
			case "code":
				png, err := qrcode.Encode(evt.Code, qrcode.Medium, 256)
				if err == nil {
					c.listener.OnQR(png)
				}
			case "timeout":
				c.listener.OnQRTimeout()
			}
		}
	}()
}

func (c *Client) PairWithPhone(phone string) (string, error) {
	return c.cli.PairPhone(context.Background(), phone, true, whatsmeow.PairClientChrome, "Chrome (Android)")
}

func (c *Client) SendText(chatJID, text string) (string, error) {
	to, err := types.ParseJID(chatJID)
	if err != nil {
		return "", err
	}
	msg := &waE2E.Message{Conversation: proto.String(text)}
	resp, err := c.cli.SendMessage(context.Background(), to, msg)
	if err != nil {
		return "", err
	}
	c.st.SaveMessage(chatJID, resp.ID, "me", text, true, time.Now().Unix(), "text", 1)
	return resp.ID, nil
}

func (c *Client) handleEvent(raw any) {
	switch evt := raw.(type) {
	case *events.Connected:
		c.listener.OnConnected()
		c.listener.OnStatus("Connected")
	case *events.Disconnected:
		c.listener.OnDisconnected()
		c.listener.OnStatus("Disconnected")
	case *events.Message:
		text := evt.Message.GetConversation()
		if text == "" && evt.Message.ExtendedTextMessage != nil {
			text = evt.Message.ExtendedTextMessage.GetText()
		}
		kind := "text"
		if evt.Message.AudioMessage != nil {
			kind = "audio"
		}
		chat := evt.Info.Chat.String()
		id := evt.Info.ID
		fromMe := evt.Info.IsFromMe
		ts := evt.Info.Timestamp.Unix()
		sender := evt.Info.Sender.String()

		c.st.SaveMessage(chat, id, sender, text, fromMe, ts, kind, 3)
		c.listener.OnNewMessage(chat, id, sender, text, fromMe, ts, kind)

	case *events.Receipt:
		for _, mid := range evt.MessageIDs {
			c.listener.OnReceipt(evt.Chat.String(), mid, int(evt.Type))
		}
	case *events.CallOffer:
		c.listener.OnCall(evt.From.String(), false, "offer")
	}
}

func (c *Client) Disconnect() {
	if c.cli != nil {
		c.cli.Disconnect()
	}
}